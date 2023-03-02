import React, { useState, useRef, useEffect } from 'react'
import MaterialTable from 'material-table';
import axios from "axios";
import { token as token,  url as baseUrl } from "./../../../api";
import NewPersonalAccessToken from './NewPersonalAccessToken'

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
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

import 'react-widgets/dist/css/react-widgets.css';
import { MdDashboard, MdDeleteForever, MdModeEdit } from "react-icons/md";
import {Menu,MenuList,MenuButton,MenuItem,} from "@reach/menu-button";
import "@reach/menu-button/styles.css";

import { makeStyles } from '@material-ui/core/styles'
import { useHistory } from "react-router-dom";


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
    } 
}))

const SettingList = (props) => {
    let history = useHistory();
  // The state for our timer
  const classes = useStyles()
  const [syncList, setSyncList] = useState( [])
  const [facilities, setFacilities] = useState( [])
  const [serverUrl, setServerUrl] = useState( [])
  const [modal, setModal] = useState(false);
  const toggle = () => setModal(!modal);
  const [showModal, setShowModal] = React.useState(false);
  const toggleModal = () => setShowModal(!showModal)
  const defaultValues = { facility: "", url: "" }
  const [uploadDetails, setUploadDetails] = useState(defaultValues);
  const [saving, setSaving] = useState(false);
 
	const Ref = useRef(null);

    useEffect(() => {
        ServerUrl()
      }, []);

    
    
    ///GET LIST OF Remote URL
    async function ServerUrl() {
        axios
            .get(`${baseUrl}sync/remote-urls`,
            { headers: {"Authorization" : `Bearer ${token}`} }
            )
            .then((response) => {
                
                setServerUrl(response.data)
                
            })
            .catch((error) => {

            });
    
    }

    const syncDataBase =()=> {        
        setShowModal(!showModal)
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
            <span style={{ textTransform: "capitalize" }}>New Personal Access Token </span>
        </Button>        
        <br/><br/>
        <br/>
      <MaterialTable
       icons={tableIcons}
        title="Personal Access Token List"
        columns={[
         // { title: " ID", field: "Id" },
          {
            title: "URLS",
            field: "name",
          },
          { title: "Username", field: "url", filtering: false },
          { title: " Status", field: "date", filtering: false },
          { title: "Action", field: "actions", filtering: false },
         
         
        ]}
        data={ [].map((row) => ({
            //Id: manager.id,
              name: row.url,
              url: row.username,
              date:  "Active",
              actions:
              <div>

                  <Menu>
                      <MenuButton style={{ backgroundColor:"#3F51B5", color:"#fff", border:"2px solid #3F51B5", borderRadius:"4px", }}>
                          Actions <span aria-hidden>▾</span>
                      </MenuButton>
                      <MenuList style={{ color:"#000 !important"}} >


                          <MenuItem  style={{ color:"#000 !important"}}>
                              <Link
                                  to ={{
                                      pathname: "/patient-dashboard",
                                      state: (row.details && row.details.hospitalNumber ? row.details.hospitalNumber : row.hospitalNumber)
                                  }}
                              >
                                  <MdDashboard size="15" color="blue" />{" "}<span style={{color: '#000'}}>Re-Generate</span>
                              </Link>
                          </MenuItem>

                          <MenuItem style={{ color:"#000 !important"}}>
                              <Link
                                  to={{
                                      pathname: "/patient-update-formio",
                                      state: (row.details && row.details.hospitalNumber ? row.details.hospitalNumber : row.hospitalNumber)
                                  }}
                              >
                                  <MdModeEdit size="15" color="blue" />{" "}<span style={{color: '#000'}}>Edit  </span>
                              </Link>
                          </MenuItem>
                          <MenuItem style={{ color:"#000 !important"}}>
                              <Link
                                  //onClick={() => onDelete(row)}
                                  >
                                  <MdDeleteForever size="15" color="blue" />{" "}
                                  <span style={{color: '#000'}}>Delete </span>
                              </Link>
                          </MenuItem>
                      </MenuList>
                  </Menu>
              </div>
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
    
    <NewPersonalAccessToken toggleModal={toggleModal} showModal={showModal} ServerUrl={ServerUrl}/>
    </div>
  );
}

export default SettingList;


