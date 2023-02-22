import React, {forwardRef} from 'react'
import Box from '@mui/material/Box';
import MaterialTable from 'material-table';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';

import AddBox from '@material-ui/icons/AddBox';
import ArrowDownward from '@material-ui/icons/ArrowDownward';
import Check from '@material-ui/icons/Check';
import ChevronLeft from '@material-ui/icons/ChevronLeft';
import ChevronRight from '@material-ui/icons/ChevronRight';
import Clear from '@material-ui/icons/Clear';
import DeleteOutline from '@material-ui/icons/DeleteOutline';
import FilterList from '@material-ui/icons/FilterList';
import FirstPage from '@material-ui/icons/FirstPage';
import LastPage from '@material-ui/icons/LastPage';
import SaveAlt from '@material-ui/icons/SaveAlt';
import Search from '@material-ui/icons/Search';
import ViewColumn from '@material-ui/icons/ViewColumn';

const tableIcons = {
    Add: forwardRef((props, ref) => <AddBox {...props} ref={ref} />),
    Check: forwardRef((props, ref) => <Check {...props} ref={ref} />),
    Clear: forwardRef((props, ref) => <Clear {...props} ref={ref} />),
    Delete: forwardRef((props, ref) => <DeleteOutline {...props} ref={ref} />),
    DetailPanel: forwardRef((props, ref) => <ChevronRight {...props} ref={ref} />),
    Export: forwardRef((props, ref) => <SaveAlt {...props} ref={ref} />),
    Filter: forwardRef((props, ref) => <FilterList {...props} ref={ref} />),
    FirstPage: forwardRef((props, ref) => <FirstPage {...props} ref={ref} />),
    LastPage: forwardRef((props, ref) => <LastPage {...props} ref={ref} />),
    NextPage: forwardRef((props, ref) => <ChevronRight {...props} ref={ref} />),
    PreviousPage: forwardRef((props, ref) => <ChevronLeft {...props} ref={ref} />),
    ResetSearch: forwardRef((props, ref) => <Clear {...props} ref={ref} />),
    Search: forwardRef((props, ref) => <Search {...props} ref={ref} />),
    SortArrow: forwardRef((props, ref) => <ArrowDownward {...props} ref={ref} />),
    ViewColumn: forwardRef((props, ref) => <ViewColumn {...props} ref={ref} />)
};


function Table() {

  return (
    <>
      <Box>
      <Card>
        <CardContent>
        <MaterialTable
         icons={tableIcons}
          title="Previous Uploads"
          columns={[
            { title: 'File Name', field: 'filename' },
            { title: 'Date', field: 'date' },
            { title: 'Status', field: 'status' },
          ]}
          data={[
            { filename: 'hts.json', date: '2/22/2023', status: "submitted" },
            { filename: 'biometrics.json', date: '2/22/2023', status: "Not submitted" },
          ]} 
          options={{
            headerStyle: {
              backgroundColor: "#014d88",
              color: "#fff",
              fontSize: "16px",
              padding: "10px",
            },
            searchFieldStyle: {
              width: "200%",
              margingLeft: "200px",
            },
            selection: false,
            filtering: false,
            exportButton: false,
            searchFieldAlignment: "left",
            pageSizeOptions: [10, 20, 100],
            pageSize: 10,
            debounceInterval: 400,
          }}       
        />
        </CardContent>
      </Card>
      </Box>
    </>
  )
}

export default Table