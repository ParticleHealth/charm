# Particle Health Node JS FHIR Client Example

## 🚀 Quick Start

```shell
  node index.js --BASE_URL=<BASE_URL> \
 --CLIENT_ID=<CLIENT_ID> \
 --CLIENT_SECRET=<CLIENT_SECRET>
```

## **Local Setup**

### Install Dependencies

- Node & NPM - https://nodejs.org/dist/v14.16.1/

```shell
  npm install
```

### Up and Running 🔥

- Get your [Particle Health](https://portal.particlehealth.com/) client_id and client_secret.

- Run the script from your favorite terminal.

NOTE: BASE_URL will default to 'https://sandbox.particlehealth.com' if not supplied. CLIENT_ID and CLIENT_SECRET are both required.

```shell
  node index.js --BASE_URL=<BASE_URL> \
 --CLIENT_ID=<CLIENT_ID> \
 --CLIENT_SECRET=<CLIENT_SECRET>
```

### Examples 🔍

The examples included in this repo are:

1. Creation of a Patient
2. Searching for a Resource
3. Searching for a Resource with search parameters (e.g. within last three years)
4. Searching for all Resources [$everything](https://www.hl7.org/fhir/patient-operation-everything.html#examples)
