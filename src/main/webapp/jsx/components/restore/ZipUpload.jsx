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
import SaveIcon from "@material-ui/icons/Save";
import CancelIcon from "@material-ui/icons/Cancel";
import axios from "axios";
import { token, url as baseUrl } from "../../../api";
import { DropzoneArea } from "material-ui-dropzone";
import SettingsBackupRestoreIcon from "@material-ui/icons/SettingsBackupRestore";
import FileUploadIcon from "@mui/icons-material/FileUpload";
import { toast } from "react-toastify";

const useStyles = makeStyles((theme) => ({
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

const ZipUpload = (props) => {
  const classes = useStyles();
  const [facilities, setFacilities] = useState([]);
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
    return Object.values(temp).every((x) => x === "");
  };

  useEffect(() => {
    Facilities();
  }, []);

  const Facilities = () => {
    axios
      .get(`${baseUrl}account`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((response) => {
        setFacilities(response.data.applicationUserOrganisationUnits);
      })
      .catch((error) => {
        //console.log(error);
      });
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setUpload({
      ...upload,
      [name]: value,
    });
  };

  const handleUploadChange = (files) => { 
    setUpload({
      ...upload,
      files: files[0],
    });
  };

  async function syncHistory() {
    axios
      .get(`${baseUrl}quick-sync/history`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((response) => {
        props.setSyncList(response.data);
      })
      .catch((error) => {});
  }

  const uploadProcess = async (e) => {
    e.preventDefault();
    if (!validateInputs()) return;
    const formData = new FormData();
    formData.append("file", upload.files);
    try {
      setLoading(true);
      const response = await axios.post(
         ` ${baseUrl}quick-sync/upload-client-zip?facilityId=${upload.facilityId}`,
          formData,
          {
            headers: { "Authorization": `Bearer ${token}` },
          }
      );
      syncHistory();
      toast.success("Sync was successful!");

    } catch (error) {
      if (error.response?.status === 400 && error.response?.data) {
        toast.error(error.response.data);
        return;
      }
      const errorMessage = error.response?.data?.apierror?.message ||
          "Something went wrong uploading. Please try again...";
      toast.error(errorMessage);

    } finally {
      setLoading(false);
      props.togglestatus();
    }
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
          <ModalHeader className={{padding: '0 2%'}} toggle={props.togglestatus}>
            Upload zip file
          </ModalHeader>
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
                        {facilities.map((value) => (
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
                      onChange={(files) => handleUploadChange(files)}
                      showFileNames="true"
                      acceptedFiles={[".zip"]}
                      maxFileSize={"1000000000"}
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

export default ZipUpload;