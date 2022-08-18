import styles from "../styles/Home.module.css";
import { useEffect, useState } from "react";
import { initFHIRClient, initiateQuery, getResources } from "../particle";

export default function Home() {
  const [client, setClient] = useState(null);
  const [status, setStatus] = useState("");

  const [family, setFamily] = useState("");
  const [given, setGiven] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [city, setCity] = useState("");
  const [postalCode, setPostalCode] = useState("");
  const [state, setState] = useState("");
  const [gender, setGender] = useState("");

  const [executePersonQuery, setExecutePersonQuery] = useState("");
  const [encounterResources, setEncounterResources] = useState(null);

  useEffect(() => {
    const execute = async () => {
      try {
        const fhirClient = await initFHIRClient();
        setClient(fhirClient);
        setStatus("OK");
      } catch (e) {
        console.error(e);
        setStatus("Something Went Wrong");
      }
    };

    execute();
  }, []);

  useEffect(() => {
    const execute = async () => {
      try {
        setStatus("Starting query - this may take a few minutes");
        const personData = {
          resourceType: "Person",
          identifier: [
            {
              type: {
                text: "ssn",
              },
              value: "123-45-6789",
            },
          ],
          name: [
            {
              family,
              given: [given],
            },
          ],
          telecom: [
            {
              system: "phone",
              value: "1-234-567-8910",
            },
            {
              system: "email",
              value: `test@doe.com`,
            },
          ],
          gender,
          birthDate,
          address: [
            {
              line: ["21 Jump Street"],
              city,
              state,
              postalCode,
            },
          ],
        };
        const { personId } = await initiateQuery(client, personData);
        setStatus("personId is set - now go get some data");
        const encounterResources = await getResources(
          client,
          "Encounter",
          personId
        );
        console.log(encounterResources);
        setEncounterResources(encounterResources);
      } catch (e) {
        console.log(e);
        setStatus("error with person query :(");
      }
    };
    if (executePersonQuery) {
      execute();
      setExecutePersonQuery(false);
    }
  }, [executePersonQuery]);

  const setDefaults = () => {
    setFamily("Klein");
    setGiven("Quinton");
    setGender("Male");
    setBirthDate("1967-10-20");
    setCity("Amesbury");
    setState("MA");
    setPostalCode("01913");
  };

  const clearForm = () => {
    setFamily("");
    setGiven("");
    setGender("");
    setBirthDate("");
    setCity("");
    setState("");
    setPostalCode("");
    setExecutePersonQuery(false);
    setEncounterResources(null);
  };

  return (
    <div className={styles.container}>
      <main className={styles.main}>
        <h1 className={styles.title}>MedCo Test App</h1>
        <h1 className={styles.title}>{status}</h1>
        <button
          onClick={() => {
            clearForm();
          }}
        >
          Clear Form
        </button>
        <button
          onClick={() => {
            setDefaults();
          }}
        >
          Set Default Patient
        </button>

        <form>
          <div>
            <label>Given Name</label>
            <input
              value={given}
              onChange={({ target: { value } }) => setGiven(value)}
            />
          </div>
          <div>
            <label>Family Name</label>
            <input
              value={family}
              onChange={({ target: { value } }) => setFamily(value)}
            />
          </div>
          <div>
            <label>Gender</label>
            <input
              value={gender}
              onChange={({ target: { value } }) => setGender(value)}
            />
          </div>
          <div>
            <label>BirthDate</label>
            <input
              value={birthDate}
              onChange={({ target: { value } }) => setBirthDate(value)}
            />
          </div>
          <div>
            <label>City</label>
            <input
              value={city}
              onChange={({ target: { value } }) => setCity(value)}
            />
          </div>
          <div>
            <label>State</label>
            <input
              value={state}
              onChange={({ target: { value } }) => setState(value)}
            />
          </div>
          <div>
            <label>PostalCode</label>
            <input
              value={postalCode}
              onChange={({ target: { value } }) => setPostalCode(value)}
            />
          </div>
        </form>
        <button
          onClick={() => {
            setExecutePersonQuery(true);
          }}
        >
          Start Query
        </button>
        {encounterResources && (
          <>
            {encounterResources.map((elt, i) => {
              return (
                <div key={i}>
                  <div>Date: {elt?.resource.period?.start}</div>
                  <div>Description: {elt?.resource?.type?.[0]?.text}</div>
                  <br />
                  <br />
                </div>
              );
            })}
          </>
        )}
      </main>
    </div>
  );
}
