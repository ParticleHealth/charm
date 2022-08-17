#!/usr/bin/env node
const fetch = require('cross-fetch')
const Client = require('fhir-kit-client')
const patientJson = require('./patient.json')
const fs = require('fs')
const yargs = require('yargs/yargs')
const { hideBin } = require('yargs/helpers')
const argv = yargs(hideBin(process.argv)).argv

const FHIR_VERSION = 'R4'
const AUTH_URL = argv.BASE_URL || 'https://sandbox.particlehealth.com'
const URL =
  `${argv.BASE_URL}/${FHIR_VERSION}` ||
  `https://sandbox.particlehealth.com/${FHIR_VERSION}`

const getToken = async () => {
  console.log('\n========= Retrieving Authentication Token =========')
  const response = await fetch(`${AUTH_URL}/auth`, {
    method: 'GET',
    headers: {
      'client-id': argv.CLIENT_ID,
      'client-secret': argv.CLIENT_SECRET,
    },
  })

  if (!response.ok) {
    console.error(`Failed to retrieve authentication token.`, response.status)
    return
  }

  console.log('Token is ready.\n')
  return await response.text()
}

const setUpPatient = async (fhirClient) => {
  console.log('========= Setting Up Patient =========')
  // Create the patient
  const fhirClientCreateRes = await fhirClient.create({
    resourceType: 'Patient',
    body: JSON.stringify(patientJson),
  })

  // Setup Query
  await fhirClient.operation({
    resourceType: 'Patient',
    name: '$query',
    id: fhirClientCreateRes.id,
    input: {
      resourceType: 'Parameters',
      parameter: [{ name: 'purpose', valueString: 'TREATMENT' }],
    },
  })

  let status, queryResponse
  console.log("Waiting for query to complete...")
  while (status !== 200) {
    try {
      queryResponse = await fhirClient.operation({
        resourceType: 'Patient',
        name: '$query',
        id: fhirClientCreateRes.id,
        method: 'get',
      })

      const { response } = Client.httpFor(queryResponse)
      status = response.status

    } catch (error) {
      console.error('Query failed to complete.', error)
      throw new Error(`Error with waiting on status ${error}`)
    }
  }
  console.log('Patient is ready.\n')
  return { patientId: fhirClientCreateRes.id, queryResponse }
}

const getResources = async (
  fhirClient,
  resourceType,
  patientId,
  searchParams = {}
) => {
  console.log(`Fetching ${resourceType}(s)...`)
  const searchResponse = await fhirClient.search({
    resourceType,
    searchParams: {
      patient: patientId,
      ...searchParams,
    },
  })

  if (searchResponse.total == 0) return null

  const promises = searchResponse.entry.map((entryData) =>
    fhirClient.read({
      resourceType: entryData.resource.resourceType,
      id: entryData.resource.id,
    })
  )

  return await Promise.all(promises)
}

const patientEverything = async (
  fhirClient,
  patientId
) => {
  console.log(`Fetching all Resources for the patient...`)
  const patientEverythingResponse = await fhirClient.operation({
    resourceType: 'Patient',
    name: '$everything',
    id: patientId,
    method: 'get',
  })

  if (patientEverythingResponse.total == 0) return null

  const promises = patientEverythingResponse.entry.map((entryData) =>
    fhirClient.read({
      resourceType: entryData.resource.resourceType,
      id: entryData.resource.id,
    })
  )

  return await Promise.all(promises)
}

  ; (async () => {
    try {
      /** Authentication **/
      const token = await getToken()
      const fhirClient = new Client({
        baseUrl: URL,
        customHeaders: {
          'Content-Type': 'application/json',
          Authorization: token,
        },
      })

      /** Patient Setup **/
      const { patientId } = await setUpPatient(fhirClient)

      /** Example 1: Retrieve an individual Resource **/
      console.log('========= Example 1: Retrieve all Encounter Resources =========')
      const resourceType = 'Encounter'
      const fileName = `all${resourceType}s.json`
      const resources = await getResources(fhirClient, resourceType, patientId)
      if (resources != null) {
        fs.writeFileSync(fileName, JSON.stringify(resources), 'utf-8')
        console.log(`Success! See generated file: ${fileName}\n`)
      } else {
        console.log(`No resources found.\n`)
      }

      /** Example 2: Retrieve a filtered Resource **/
      console.log('========= Example 2: Retrieve Encounter Resources from the last three years =========')
      const today = new Date()
      const lastMonth = new Date(
        today.getFullYear() - 3,
        today.getMonth(),
        today.getDate()
      ).toISOString()
      const filteredResource = 'Encounter'
      const filteredResourceFileName = `filtered${filteredResource}s.json`
      const filteredResourceResults = await getResources(
        fhirClient,
        filteredResource,
        patientId,
        {
          date: `gt${lastMonth}`,
        }
      )
      if (filteredResourceResults != null) {
        fs.writeFileSync(
          filteredResourceFileName,
          JSON.stringify(filteredResourceResults),
          'utf-8'
        )
        console.log(`Success! See generated file: ${filteredResourceFileName}\n`)
      } else {
        console.log("No resources found.\n")
      }

      /** Example 3. Retrieve all Resources for a patient **/
      console.log('========= Example 3: Retrieve all Resources for a patient =========')
      const everythingFileName = `patientEverything.json`
      const everythingResource = await patientEverything(fhirClient, patientId)
      if (everythingResource != null) {
        fs.writeFileSync(everythingFileName, JSON.stringify(everythingResource), 'utf-8')
        console.log(`Success! See generated file: ${everythingFileName}\n`)
      } else {
        console.log(`No resources found.\n`)
      }

    } catch (err) {
      console.error(err)
    }
  })()
