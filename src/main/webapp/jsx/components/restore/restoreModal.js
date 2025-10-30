import React, { useState, useEffect } from "react";
import {
  Modal,
  ModalHeader,
  ModalBody,
  Form,
  FormFeedback,
  Row,
  Col,
  Card,
  CardBody,
  FormGroup,
  Label,
  Input,
} from "reactstrap";
import MatButton from "@material-ui/core/Button";
import { makeStyles } from "@material-ui/core/styles";
import CancelIcon from "@material-ui/icons/Cancel";
import axios from "axios";
import { token, url as baseUrl } from "../../../api";
import { DropzoneArea } from "material-ui-dropzone";
import FileUploadIcon from "@mui/icons-material/FileUpload";
import { toast } from "react-toastify";

const useStyles = makeStyles(theme => ({
  card: {
    margin: theme.spacing(20),
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
  },
  form: {
    width: "100%", // Fix IE 11 issue.
    marginTop: theme.spacing(3),
  },
  submit: {
    margin: theme.spacing(3, 0, 2),
  },
  cardBottom: {
    marginBottom: 20,
  },
  Select: {
    height: 45,
    width: 350,
  },
  button: {
    margin: theme.spacing(1),
  },

  root: {
    "& > *": {
      margin: theme.spacing(1),
    },
  },
  input: {
    display: "none",
  },
}));

const DatabaseRestore = props => {
  const classes = useStyles();
  const [facilities, setFacilities] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [upload, setUpload] = useState({
    facilityId: "",
    files: [],
  });

  const validateInputs = () => {
    let temp = { ...errors };
    temp.facilityId = upload.facilityId ? "" : "Facility name is required.";

    setErrors({
      ...temp,
    });
    return Object.values(temp).every(x => x === "");
  };

  useEffect(() => {
    Facilities();
    getCurrentUser();
  }, []);

  const Facilities = () => {
    axios
      .get(`${baseUrl}account`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(response => {
        setFacilities(response.data.applicationUserOrganisationUnits);
      })
      .catch(error => {
        //console.log(error);
      });
  };

  const getCurrentUser = () => {
    axios
      .get(`${baseUrl}account`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(response => {
        setCurrentUser(response.data);
      })
      .catch(error => {
        console.log("Error fetching current user:", error);
      });
  };

  const handleInputChange = e => {
    const { name, value } = e.target;
    setUpload({
      ...upload,
      [name]: value,
    });
  };

  const handleUploadChange = files => {
    setUpload({
      ...upload,
      files: files[0],
    });
  };

  const validateBiometricFacility = async (file) => {
    return new Promise((resolve, reject) => {
      if (!currentUser || !currentUser.currentOrganisationUnitId) {
        reject(new Error("User session not found. Please refresh the page."));
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const jsonContent = JSON.parse(e.target.result);
          // Get the current user's facility ID from state
          const userFacilityId = currentUser.currentOrganisationUnitId;

          // Check each record in the array
          if (Array.isArray(jsonContent)) {
            for (let i = 0; i < jsonContent.length; i++) {
              const record = jsonContent[i];
              const fileFacilityId = record?.person?.facilityId;
              // Convert both to numbers for comparison to avoid type mismatch
              if (fileFacilityId && userFacilityId) {
                const fileFacilityIdNum = Number(fileFacilityId);
                const userFacilityIdNum = Number(userFacilityId);

                if (fileFacilityIdNum !== userFacilityIdNum) {
                  reject(new Error("Upload failed: This file belongs to a different facility. Please ensure you are uploading the correct file for your assigned facility."));
                  return;
                }
              }
            }
          }
          resolve();
        } catch (error) {
          reject(new Error("Invalid JSON file format"));
        }
      };
      reader.onerror = () => reject(new Error("Failed to read file"));
      reader.readAsText(file);
    });
  };

  async function syncHistory() {
    axios
      .get(`${baseUrl}quick-sync/history`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(response => {
        props.setSyncList(response.data);
      })
      .catch(error => {});
  }

  const uploadProcess = async e => {
    e.preventDefault();
    if (validateInputs()) {
      let fileName = upload.files.name;

      const formData = new FormData();

      formData.append("file", upload.files);

      // Validate biometric file facility before uploading
      if (fileName.includes("biometrics") === true) {
        try {
          await validateBiometricFacility(upload.files);
        } catch (error) {
          toast.error(error.message);
          return;
        }
      }

      if (fileName.includes("patient") === true) {
        axios
          .post(
            `${baseUrl}quick-sync/import/person-data?facilityId=${upload.facilityId}`,
            formData,
            {
              headers: { Authorization: `Bearer ${token}` },
              responseType: "blob",
            }
          )
          .then(response => {
            setLoading(false);
            syncHistory();
            toast.success("Patient Json uploaded successfully");
          })
          .catch(error => {
            setLoading(false);
            if (error.response && error.response.data) {
              let errorMessage =
                error.response.data.apierror &&
                error.response.data.apierror.message !== ""
                  ? error.response.data.apierror.message
                  : "Something went wrong uploading, please try again";
              toast.error(errorMessage);
            } else {
              toast.error(
                "Something went wrong uploading. Please try again..."
              );
            }
          });
      } else if (fileName.includes("biometrics") === true) {
        axios
          .post(
            `${baseUrl}quick-sync/import/biometric-data?facilityId=${upload.facilityId}`,
            formData,
            {
              headers: { Authorization: `Bearer ${token}` },
              responseType: "blob",
            }
          )
          .then(response => {
            setLoading(false);
            syncHistory();
            toast.success("Biometrics Json uploaded successfully");
          })
          .catch(error => {
            setLoading(false);
            if (error.response && error.response.data) {
              let errorMessage =
                error.response.data.apierror &&
                error.response.data.apierror.message !== ""
                  ? error.response.data.apierror.message
                  : "Something went wrong uploading, please try again";
              toast.error(errorMessage);
            } else {
              toast.error(
                "Something went wrong uploading. Please try again..."
              );
            }
          });
      } else if (fileName.toLowerCase().includes("lamisplus-export") === true) {
        axios
          .post(
            `${baseUrl}quick-sync/upload-client-zip?facilityId=${upload.facilityId}`,
            formData,
            {
              headers: { Authorization: `Bearer ${token}` },
            }
          )
          .then(response => {
            setLoading(false);
            syncHistory();
            toast.success("HTS sync successfully");
          })
          .catch(error => {
            setLoading(false);
            if (error.response && error.response?.data) {
              let errorMessage =
                error.response?.data && error.response.data !== ""
                  ? error.response?.data
                  : "Something went wrong uploading, please try again";
              toast.error(errorMessage);
            } else {
              toast.error(
                "Something went wrong uploading. Please try again..."
              );
            }
          });
      } else {
        toast.error(
          "Upload failed. Please verify the filename format and retry."
        );
        return null;
      }
    }

    props.togglestatus();
  };

  return (
    <div>
      <Modal
        isOpen={props.modalstatus}
        toggle={props.togglestatus}
        className={props.className}
        size="lg"
      >
        <Form>
          <ModalHeader toggle={props.togglestatus}>
            Upload JSON File
          </ModalHeader>
          {/* <ModalHeader toggle={props.togglestatus}>
            Upload JSON File
          </ModalHeader> */}
          <ModalBody>
            <Card>
              <CardBody>
                <Row>
                  <Col md={12}>
                    <FormGroup>
                      <Label for="exampleSelect">
                        Facility Name <span style={{ color: "red" }}> *</span>
                      </Label>
                      <Input
                        type="select"
                        name="facilityId"
                        id="facility"
                        onChange={handleInputChange}
                        style={{
                          border: "1px solid #014D88",
                          borderRadius: "0.2rem",
                        }}
                      >
                        <option value={""}></option>
                        {facilities.map(value => (
                          <option
                            key={value.id}
                            value={value.organisationUnitId}
                          >
                            {value.organisationUnitName}
                          </option>
                        ))}
                      </Input>
                      {errors.facilityId !== "" ? (
                        <span style={{ color: "#f85032", fontSize: "11px" }}>
                          {errors.facilityId}
                        </span>
                      ) : (
                        ""
                      )}
                    </FormGroup>
                  </Col>
                  <Col md={12}>
                    <DropzoneArea
                      onChange={files => handleUploadChange(files)}
                      showFileNames="true"
                      acceptedFiles={[".json", ".zip"]}
                      maxFileSize={"100000000"}
                      filesLimit={1}
                    />
                  </Col>
                </Row>
                <br />

                <br />

                <MatButton
                  type="submit"
                  variant="contained"
                  color="primary"
                  className={classes.button}
                  startIcon={<FileUploadIcon />}
                  onClick={uploadProcess}
                >
                  Upload
                </MatButton>

                <MatButton
                  variant="contained"
                  color="default"
                  onClick={props.togglestatus}
                  className={classes.button}
                  startIcon={<CancelIcon />}
                >
                  Cancel
                </MatButton>
              </CardBody>
            </Card>
          </ModalBody>
        </Form>
      </Modal>
    </div>
  );
};

export default DatabaseRestore;
