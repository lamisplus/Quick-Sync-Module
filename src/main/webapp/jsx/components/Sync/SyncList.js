import React, { useState, useRef, useEffect } from 'react'
import MaterialTable from 'material-table';
import axios from "axios";
import { token as token,  url as baseUrl } from "./../../../api";
import { forwardRef } from 'react';
import { Link } from 'react-router-dom'
import AddBox from '@material-ui/icons/AddBox';
import ArrowUpward from '@material-ui/icons/ArrowUpward';
import Check from '@material-ui/icons/Check';
import ChevronLeft from '@material-ui/icons/ChevronLeft';
import ChevronRight from '@material-ui/icons/ChevronRight';
import Clear from '@material-ui/icons/Clear';
import DeleteOutline from '@material-ui/icons/DeleteOutline';
import Edit from '@material-ui/icons/Edit';
import FilterList from '@material-ui/icons/FilterList';
import FirstPage from '@material-ui/icons/FirstPage';
import LastPage from '@material-ui/icons/LastPage';
import Remove from '@material-ui/icons/Remove';
import SaveAlt from '@material-ui/icons/SaveAlt';
import Search from '@material-ui/icons/Search';
import ViewColumn from '@material-ui/icons/ViewColumn';

import Button from "@material-ui/core/Button";
import {  toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
//import { Menu, MenuList, MenuButton, MenuItem } from "@reach/menu-button";
import "@reach/menu-button/styles.css";
//import { MdModeEdit } from "react-icons/md";
import 'react-widgets/dist/css/react-widgets.css';
import { Modal, ModalHeader, ModalBody,Form,FormFeedback,
    Row,Col, Card,CardBody, FormGroup, Label, Input} from 'reactstrap';
import MatButton from '@material-ui/core/Button'
import { makeStyles } from '@material-ui/core/styles'
//import SaveIcon from '@material-ui/icons/Save'
import CancelIcon from '@material-ui/icons/Cancel'
import { Alert } from 'reactstrap';
import { Spinner } from 'reactstrap';
//import { DropzoneArea } from 'material-ui-dropzone';
import SettingsBackupRestoreIcon from '@material-ui/icons/SettingsBackupRestore';
import { useHistory } from "react-router-dom";
//import { compileString } from 'sass';


const tableIcons = {
Add: forwardRef((props, ref) => <AddBox {...props} ref={ref} />),
Check: forwardRef((props, ref) => <Check {...props} ref={ref} />),
Clear: forwardRef((props, ref) => <Clear {...props} ref={ref} />),
Delete: forwardRef((props, ref) => <DeleteOutline {...props} ref={ref} />),
DetailPanel: forwardRef((props, ref) => <ChevronRight {...props} ref={ref} />),
Edit: forwardRef((props, ref) => <Edit {...props} ref={ref} />),
Export: forwardRef((props, ref) => <SaveAlt {...props} ref={ref} />),
Filter: forwardRef((props, ref) => <FilterList {...props} ref={ref} />),
FirstPage: forwardRef((props, ref) => <FirstPage {...props} ref={ref} />),
LastPage: forwardRef((props, ref) => <LastPage {...props} ref={ref} />),
NextPage: forwardRef((props, ref) => <ChevronRight {...props} ref={ref} />),
PreviousPage: forwardRef((props, ref) => <ChevronLeft {...props} ref={ref} />),
ResetSearch: forwardRef((props, ref) => <Clear {...props} ref={ref} />),
Search: forwardRef((props, ref) => <Search {...props} ref={ref} />),
SortArrow: forwardRef((props, ref) => <ArrowUpward {...props} ref={ref} />),
ThirdStateCheck: forwardRef((props, ref) => <Remove {...props} ref={ref} />),
ViewColumn: forwardRef((props, ref) => <ViewColumn {...props} ref={ref} />)
};

const useStyles = makeStyles(theme => ({
    card: {
        margin: theme.spacing(20),
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
    },
    form: {
        width: '100%', // Fix IE 11 issue.
        marginTop: theme.spacing(3)
    },
    submit: {
        margin: theme.spacing(3, 0, 2)
    },
    cardBottom: {
        marginBottom: 20
    },
    Select: {
        height: 45,
        width: 350
    },
    button: {
        margin: theme.spacing(1)
    },

    root: {
        '& > *': {
            margin: theme.spacing(1)
        }
    },
    input: {
        display: 'none'
    },
    error: {
        color: "#f85032",
        fontSize: "11px",
    },
    success: {
        color: "#4BB543 ",
        fontSize: "11px",
    }, 
}))

const SyncList = (props) => {
    let history = useHistory();
  // The state for our timer
  const classes = useStyles()
  const [syncList, setSyncList] = useState( [])
  const [facilities, setFacilities] = useState( [])
  const [serverUrl, setServerUrl] = useState( [])
  const [modal, setModal] = useState(false);
  const toggle = () => setModal(!modal);
  const defaultValues = { facilityId: "", serverUrl : "" }
  const [uploadDetails, setUploadDetails] = useState(defaultValues);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});
 
	const Ref = useRef(null);

    useEffect(() => {
        syncHistory()
        Facilities()
        ServerUrl()
        const timer = setTimeout(() => console.log('Initial timeout!'), 1);
        return () => clearTimeout(timer);
      }, []);
    
          /*****  Validation */
    const validate = () => {
        let temp = { ...errors };
        temp.facilityId = uploadDetails.facilityId
            ? ""
            : "Facility is required";
            temp.serverUrl  = uploadDetails.serverUrl 
            ? ""
            : "URL is required";
            
            setErrors({
                ...temp,
            });
            return Object.values(temp).every((x) => x === "");
        };
     ///GET LIST OF Sync History
    async function syncHistory() {

        axios
            .get(`${baseUrl}sync/sync-history`,
           { headers: {"Authorization" : `Bearer ${token}`} }
            )
            .then((response) => {
                setSyncList(response.data);
            })
            .catch((error) => {

            });
    
    }
    ///GET LIST OF Facilities
    async function Facilities() {
        axios
            .get(`${baseUrl}sync/facilities`,
            { headers: {"Authorization" : `Bearer ${token}`} }
            )
            .then((response) => {
                
                setFacilities(
                    Object.entries(response.data).map(([key, value]) => ({
                        label: value.name,
                        value: value.id,
                      }))
                );
            })
            .catch((error) => {

            });
    
    }
    ///GET LIST OF Facilities
    async function ServerUrl() {
        axios
            .get(`${baseUrl}sync/remote-urls`,
             { headers: {"Authorization" : `Bearer ${token}`} }
            )
            .then((response) => {
                
                setServerUrl(
                    Object.entries(response.data).map(([key, value]) => ({
                        label: value.url + " - " + value.username,
                        value: value.url,
                      }))
                );
            })
            .catch((error) => {

            });
    
    }

    const handleInputChange = e => {
        setUploadDetails ({...uploadDetails,  [e.target.name]: e.target.value});
    }

    const handleSubmit = (e) => {
       
        e.preventDefault();
             
            
           if(validate()){
            setSaving(true);
            axios.post(`${baseUrl}sync/upload`, uploadDetails,
            { headers: {"Authorization" : `Bearer ${token}`}},
            )
                        .then(response => {
                            setSaving(false);
                            toast.success("Upload Successful");
                            syncHistory();
                            //Closing of the modal 
                            toggle();
                        })
                        .catch(error => {
                            setSaving(false);
                            toast.error("Something went wrong");
                        });
             }       
      };

    const syncDataBase =()=> {        
        setModal(!modal)
    }
    
 
  return (
    <div>
       
        <Button
            variant="contained"
            color="primary"
            className=" float-right mr-1"
            //startIcon={<FaUserPlus />}
            onClick={syncDataBase}
          >
            <span style={{ textTransform: "capitalize" }}>Upload </span>
        </Button>        
        <br/><br/>
        <br/>
      <MaterialTable
       icons={tableIcons}
        title="Sync List "
        columns={[
         // { title: " ID", field: "Id" },
          {
            title: "Facility Name",
            field: "name",
          },
          { title: "Table Name", field: "url", filtering: false },
          { title: "Upload Size", field: "uploadSize", filtering: false },
          { title: "Date of Upload ", field: "date", filtering: false },
          { title: "Status", field: "status", filtering: false },        
         
        ]}
        data={ [].map((row) => ({
            //Id: manager.id,
              name: row.facilityName,
              url: row.tableName,
              uploadSize: row.uploadSize,
              date:  row.dateLastSync,
              status: row.processed===0 ? "Processing" : "Completed",
            
            }))}
       
                  options={{
                    headerStyle: {
                        backgroundColor: "#9F9FA5",
                        color: "#000",
                    },
                    searchFieldStyle: {
                        width : '200%',
                        margingLeft: '250px',
                    },
                    filtering: false,
                    exportButton: false,
                    searchFieldAlignment: 'left',
                    pageSizeOptions:[10,20,100],
                    pageSize:10,
                    debounceInterval: 400
                }}
      />
    
    <Modal isOpen={modal} toggle={toggle} className={props.className} size="lg" backdrop={false} backdrop="static">
              <Form >
             <ModalHeader toggle={toggle}>Upload</ModalHeader>
                <ModalBody>
                    
                        <Card >
                            <CardBody>
                                <Row >
                                  
                                    <Col md={12}>
                                    <FormGroup>
                                    <Label >Facility *</Label>
                                            <Input
                                                type="select"
                                                name="facilityId"
                                                id="facilityId"
                                                onChange={handleInputChange}
                                                required
                                                >
                                                <option > </option>
                                                {facilities.map(({ label, value }) => (
                                                    <option key={value} value={value}>
                                                    {label}
                                                    </option>
                                                ))}
                                            </Input>
                                            {errors.facilityId !=="" ? (
                                                <span className={classes.error}>{errors.facilityId}</span>
                                            ) : "" } 
                                    </FormGroup>
                                    </Col> 
                                    <Col md={12}>
                                    <FormGroup>
                                    <Label >URL *</Label>
                                            <Input
                                                type="select"
                                                name="serverUrl"
                                                id="serverUrl"
                                                onChange={handleInputChange}
                                                required
                                                >
                                                <option > </option>
                                                {serverUrl.map(({ label, value }) => (
                                                    <option key={value} value={value}>
                                                    {label}
                                                    </option>
                                                ))}
                                            </Input>
                                            {errors.serverUrl !=="" ? (
                                                <span className={classes.error}>{errors.serverUrl}</span>
                                            ) : "" } 
                                    </FormGroup>
                                    </Col>                    
                                  </Row>
                                      <br/>
                                      {saving ? <Spinner /> : ""}
                                        <br />
                                      
                                          <MatButton
                                              type='submit'
                                              variant='contained'
                                              color='primary'
                                              className={classes.button}
                                              startIcon={<SettingsBackupRestoreIcon />}
                                              onClick={handleSubmit}
                                             
                                          >   
                                             {!saving ? (
                                                <span style={{ textTransform: "capitalize" }}>Upload</span>
                                                ) : (
                                                <span style={{ textTransform: "capitalize" }}>Uploading Please Wait...</span>
                                                )
                                            } 
                                          </MatButton>
                                           
                                          <MatButton
                                              variant='contained'
                                              color='default'
                                              onClick={toggle}
                                              className={classes.button}
                                              startIcon={<CancelIcon />}
                                          >
                                              <span style={{ textTransform: "capitalize " }}>cancel</span>
                                          </MatButton>
                            </CardBody>
                        </Card> 
                    </ModalBody>
        
                </Form>
      </Modal>
    </div>
  );
}

export default SyncList;


