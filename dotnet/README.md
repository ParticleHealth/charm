# Particle Health .NET FHIR Client Sample

## Quick Start 🚀

```shell
export PARTICLE_HOST=https://sandbox.particlehealth.com \
export CLIENT_ID=<CLIENT_ID> \
export CLIENT_SECRET=<CLIENT_SECRET>
```

```shell
dotnet run csharp-fhir-client.csproj
```

## Local Setup

### Install Dependencies

- .NET SDK 6.0 (https://dotnet.microsoft.com/en-us/download/dotnet/6.0)

### Running the Application 🔥

- Get your `CLIENT_ID` and `CLIENT_SECRET` from the [Particle Health Portal](https://portal.particlehealth.com/).

- Set the environment variables:

```shell
export PARTICLE_HOST=https://sandbox.particlehealth.com \
export CLIENT_ID=<CLIENT_ID> \
export CLIENT_SECRET=<CLIENT_SECRET>
```

- Run the application:

```shell
dotnet run csharp-fhir-client.csproj
```

## Examples 🔍

The examples included in this project are:

1. Creating a Patient.
2. Querying a Patient.
3. Fetching a Patient's [Medication Statements](https://www.hl7.org/fhir/medicationstatement.html).
4. Fetching all of a Patient's resources ([$everything](https://hl7.org/fhir/operation-patient-everything.html)).