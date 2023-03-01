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
import { Alert } from "reactstrap";
import { Spinner } from "reactstrap";
import axios from "axios";
import { token, url as baseUrl } from "../../../api";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import { toast } from "react-toastify";
import FileSaver from "file-saver";

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

const DownloadModal = (props) => {
  const classes = useStyles();
  let currentDate = new Date().toISOString().split("T")[0];
  const [loading, setLoading] = useState(false);
  const [download, setDownload] = useState({
    facilityId: "",
    startDate: "",
    endDate: "",
    program: ""
  });
  const [organisationUnitName, setOrganisationUnitName] = useState("");

  const [errors, setErrors] = useState({});

  const validateInputs = () => {
    let temp = { ...errors };
    temp.facilityId = download.facilityId ? "" : "Facility name is required.";
    temp.startDate = download.startDate ? "" : "Start date is required.";
    temp.endDate = download.endDate ? "" : "End date is required.";
    temp.program = download.program ? "" : "Program is required.";
    setErrors({
      ...temp,
    });
    return Object.values(temp).every((x) => x === "");
  };

  const [facilities, setFacilities] = useState([]);

  useEffect(() => {
    Facilities();
  }, []);

  const Facilities = () => {
    axios
      .get(`${baseUrl}account`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((response) => {
        // console.log(response.data);
        setFacilities(response.data.applicationUserOrganisationUnits);
      })
      .catch((error) => {
        //console.log(error);
      });
  };

  const handleInputChange = (e) => {
    const { name, value, innerText, id } = e.target;

    if (innerText !== "" && id === "facility" ) {
      console.log(innerText)
      setOrganisationUnitName(innerText);
    }

    setDownload({
      ...download,
      [name]: value
    });
  };

  const DatabaseRestoreProcess = (e) => {
    e.preventDefault();

    //console.log("data", download);
    if (validateInputs()) {
      axios
        .get(
          `${baseUrl}quick-sync/export/person-data?facilityId=${download.facilityId}&startDate=${download.startDate}&endDate=${download.endDate}`,
          {
            headers: { Authorization: `Bearer ${token}` },
            responseType: "blob",
          }
        )
        .then((response) => {
          //console.log(response);
          setLoading(false);
          const fileName = `${organisationUnitName} ${download.program} ${currentDate}`;
          const responseData = response.data;
          let blob = new Blob([responseData], {
            type: "application/octet-stream",
          });

          FileSaver.saveAs(blob, `${fileName}.json`);
          toast.success("Json generated successfully");
        })
        .catch((error) => {
          setLoading(false);
          if (error.response && error.response.data) {
            let errorMessage =
              error.response.data.apierror &&
              error.response.data.apierror.message !== ""
                ? error.response.data.apierror.message
                : "Something went wrong, please try again";
            toast.error(errorMessage);
          } else {
            toast.error("Something went wrong. Please try again...");
          }
        });
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
            Download JSON File
          </ModalHeader>
          <ModalBody>
            <Card>
              <CardBody>
                <Row>
                  <Col md={6}>
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
                  <Col md={6}>
                    <FormGroup>
                      <Label for="exampleSelect">
                        Program <span style={{ color: "red" }}> *</span>
                      </Label>
                      <Input
                        type="select"
                        name="program"
                        id="program"
                        onChange={handleInputChange}
                        style={{
                          border: "1px solid #014D88",
                          borderRadius: "0.2rem",
                        }}
                      >
                        <option value={"biometrics"}>Biometrics</option>
                        <option value={"hts"}>HTS</option>
                        <option value={"patient"}>Patient</option>

                      </Input>
                      {errors.program !== "" ? (
                        <span style={{ color: "#f85032", fontSize: "11px" }}>
                          {errors.program}
                        </span>
                      ) : (
                        ""
                      )}
                    </FormGroup>
                  </Col>
                </Row>
                <Row>
                <Col md={6}>
                    <FormGroup>
                      <Label>
                        From <span style={{ color: "red" }}> *</span>
                      </Label>
                      <input
                        type="date"
                        className="form-control"
                        name="startDate"
                        id="startDate"
                        min={"1980-01-01"}
                        max={currentDate}
                        value={download.startDate}
                        onChange={handleInputChange}
                        style={{
                          border: "1px solid #014D88",
                          borderRadius: "0.2rem",
                        }}
                        required
                      />
                      {errors.startDate !== "" ? (
                        <span style={{ color: "#f85032", fontSize: "11px" }}>
                          {errors.startDate}
                        </span>
                      ) : (
                        ""
                      )}
                    </FormGroup>
                  </Col>
                  <Col md={6}>
                    <FormGroup>
                      <Label>
                        To <span style={{ color: "red" }}> *</span>
                      </Label>
                      <input
                        type="date"
                        className="form-control"
                        name="endDate"
                        id="endDate"
                        min={"1980-01-01"}
                        max={currentDate}
                        //min={objValues.startDate}
                        value={download.endDate}
                        onChange={handleInputChange}
                        style={{
                          border: "1px solid #014D88",
                          borderRadius: "0.2rem",
                        }}
                        required
                      />
                      {errors.endDate !== "" ? (
                        <span style={{ color: "#f85032", fontSize: "11px" }}>
                          {errors.endDate}
                        </span>
                      ) : (
                        ""
                      )}
                    </FormGroup>
                  </Col>
                </Row>
                <br />

                <br />

                <MatButton
                  type="submit"
                  variant="contained"
                  color="primary"
                  className={classes.button}
                  startIcon={<ArrowDownwardIcon />}
                  onClick={DatabaseRestoreProcess}
                  disabled={download.program === "" ? true : false}
                >
                  Download
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

export default DownloadModal;
