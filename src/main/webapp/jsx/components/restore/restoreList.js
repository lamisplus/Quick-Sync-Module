import React, { useState, useRef, useEffect } from "react";
import MaterialTable from "material-table";
import axios from "axios";
import { token as token, url as baseUrl } from "./../../../api";
import { forwardRef } from "react";
import { Link } from "react-router-dom";

import AddBox from "@material-ui/icons/AddBox";
import ArrowUpward from "@material-ui/icons/ArrowUpward";
import Check from "@material-ui/icons/Check";
import ChevronLeft from "@material-ui/icons/ChevronLeft";
import ChevronRight from "@material-ui/icons/ChevronRight";
import Clear from "@material-ui/icons/Clear";
import DeleteOutline from "@material-ui/icons/DeleteOutline";
import Edit from "@material-ui/icons/Edit";
import FilterList from "@material-ui/icons/FilterList";
import FirstPage from "@material-ui/icons/FirstPage";
import LastPage from "@material-ui/icons/LastPage";
import Remove from "@material-ui/icons/Remove";
import SaveAlt from "@material-ui/icons/SaveAlt";
import Search from "@material-ui/icons/Search";
import ViewColumn from "@material-ui/icons/ViewColumn";

import Button from "@material-ui/core/Button";
import { Badge, Spinner } from "reactstrap";
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import RestoreModal from "./restoreModal";
import DownloadModal from "./DownloadModal";

const tableIcons = {
  Add: forwardRef((props, ref) => <AddBox {...props} ref={ref} />),
  Check: forwardRef((props, ref) => <Check {...props} ref={ref} />),
  Clear: forwardRef((props, ref) => <Clear {...props} ref={ref} />),
  Delete: forwardRef((props, ref) => <DeleteOutline {...props} ref={ref} />),
  DetailPanel: forwardRef((props, ref) => (
    <ChevronRight {...props} ref={ref} />
  )),
  Edit: forwardRef((props, ref) => <Edit {...props} ref={ref} />),
  Export: forwardRef((props, ref) => <SaveAlt {...props} ref={ref} />),
  Filter: forwardRef((props, ref) => <FilterList {...props} ref={ref} />),
  FirstPage: forwardRef((props, ref) => <FirstPage {...props} ref={ref} />),
  LastPage: forwardRef((props, ref) => <LastPage {...props} ref={ref} />),
  NextPage: forwardRef((props, ref) => <ChevronRight {...props} ref={ref} />),
  PreviousPage: forwardRef((props, ref) => (
    <ChevronLeft {...props} ref={ref} />
  )),
  ResetSearch: forwardRef((props, ref) => <Clear {...props} ref={ref} />),
  Search: forwardRef((props, ref) => <Search {...props} ref={ref} />),
  SortArrow: forwardRef((props, ref) => <ArrowUpward {...props} ref={ref} />),
  ThirdStateCheck: forwardRef((props, ref) => <Remove {...props} ref={ref} />),
  ViewColumn: forwardRef((props, ref) => <ViewColumn {...props} ref={ref} />),
};

const RestoreList = (props) => {
  const [syncList, setSyncList] = useState([]);
  const [modal, setModal] = useState(false);
  const [modalDownload, setModalDownload] = useState(false);

  const toggle = () => setModal(!modal);
  const toggleDownload = () => setModalDownload(!modalDownload);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    syncHistory();

  }, []);

  async function syncHistory() {
    axios
      .get(`${baseUrl}quick-sync/history`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((response) => {
        console.log("sync",response.data)
        setSyncList(response.data);
      })
      .catch((error) => {});
  }

  const syncDataBase = () => {
    setModal(!modal);
  };

  const syncDownload = () => {
    setModalDownload(!modalDownload);
  };

  const PageTitle = ({ motherMenu, activeMenu, pageContent }) => {
    let path = window.location.pathname.split("/");
  	return (
  		<div className="row page-titles mx-0">
  			<ol className="breadcrumb">
  				<li className="breadcrumb-item active"><Link to={`/${path[path.length - 1]}`}>{motherMenu}</Link></li>
  				<li className="breadcrumb-item  "><Link to={`/${path[path.length - 1]}`}>{activeMenu}</Link></li>
  			</ol>
  		</div>
  	);
  };


  return (
    <>
      <PageTitle activeMenu="Quick Sync" motherMenu="Sync " />
      <Button
        variant="contained"
        color="primary"
        className=" float-right mr-1"
        startIcon={<UploadIcon />}
        onClick={syncDataBase}
        style={{backgroundColor:'#014d88',fontWeight:"bolder"}}
      >
        <span style={{ textTransform: "capitalize" }}>Upload </span>
      </Button>
      <Button
        variant="contained"
        color="primary"
        className=" float-right mr-1"
        startIcon={<DownloadIcon />}
        onClick={syncDownload}
        style={{backgroundColor:'#014d88',fontWeight:"bolder"}}
      >
        <span style={{ textTransform: "capitalize" }}>Download </span>
      </Button>
      <br />
      <br />
      <br />
      <MaterialTable
        icons={tableIcons}
        title="Quick Sync Upload List "
        columns={[
          { title: "Facility Name",field: "name"},
          { title: "Table Name", field: "url", filtering: false },
          { title: "Upload Size", field: "uploadSize", filtering: false },
          { title: "Date of Upload ", field: "date", filtering: false },
          { title: "Status", field: "status", filtering: false },
        ]}
        data={syncList.map((row) => ({
          name: row.filename,
          url: row.tableName,
          uploadSize: row.fileSize,
          date: row.dateCreated.replace("T", " "),
          status: <Badge color="info">{row.status}</Badge>,
        }))}
        options={{
          headerStyle: {
            backgroundColor: "#014d88",
            color: "#fff",
          },
          searchFieldStyle: {
            width: "200%",
            margingLeft: "250px",
          },
          filtering: false,
          exportButton: false,
          searchFieldAlignment: "left",
          pageSizeOptions: [10, 20, 100],
          pageSize: 10,
          debounceInterval: 400,
        }}
      />
      <RestoreModal modalstatus={modal} togglestatus={toggle} />
      <DownloadModal
        modalstatus={modalDownload}
        togglestatus={toggleDownload}
        setSyncList={setSyncList}
      />
    </>
  );
};

export default RestoreList;
