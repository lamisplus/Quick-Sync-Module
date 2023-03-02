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
import { Modal, ModalHeader, ModalBody,Form,
    Row,Col, Card,CardBody, FormGroup, Label, Input} from 'reactstrap';
import Button from "@material-ui/core/Button";
import { Spinner } from 'reactstrap';
import SettingsBackupRestoreIcon from '@material-ui/icons/SettingsBackupRestore';
import BackUpModal from './backupModal'


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

const BackUpList = (props) => {

  const [syncList, setSyncList] = useState( [])

  const [modal, setModal] = useState(false);
  const toggle = () => setModal(!modal);
  const [saving, setSaving] = useState(false);
    useEffect(() => {
        syncHistory()
        // const timer = setTimeout(() => console.log('Initial timeout!'), 1);
        // return () => clearTimeout(timer);
      }, []);

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
            <span style={{ textTransform: "capitalize" }}>BackUp Database </span>
        </Button>
        <br/><br/>
        <br/>
      <MaterialTable
       icons={tableIcons}
        title="Backup List "
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
    <BackUpModal modalstatus={modal} togglestatus={toggle} />
    </div>
  );
}

export default BackUpList;


