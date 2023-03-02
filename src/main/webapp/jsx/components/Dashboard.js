import React, {useEffect, useState} from "react";
import { makeStyles } from "@material-ui/core/styles";
import AppBar from "@material-ui/core/AppBar";
import Tabs from "@material-ui/core/Tabs";
import Tab from "@material-ui/core/Tab";
import Typography from "@material-ui/core/Typography";

import Box from "@material-ui/core/Box";
import PropTypes from "prop-types";
import Moment from "moment";
import momentLocalizer from "react-widgets-moment";

import {getQueryParams} from "./../components/Utils/PageUtils";

import Menu from '@material-ui/core/Menu';
import MenuItem from '@material-ui/core/MenuItem';
import Fade from '@material-ui/core/Fade'
import SyncList from './Sync/SyncList';
import Setting from './Settings/index'
import Backup from './backup/backupList'
import Restore from './restore/restoreList'
import RestoreIcon from '@material-ui/icons/Restore';
//import SettingsBackupRestoreIcon from '@material-ui/icons/SettingsBackupRestore';
import BackupIcon from '@material-ui/icons/Backup';
import SettingsIcon from '@mui/icons-material/Settings';
import CloudSyncIcon from '@mui/icons-material/CloudSync';

//Dtate Picker package
Moment.locale("en");
momentLocalizer();

const useStyles = makeStyles((theme) => ({
  header: {
    fontSize: "20px",
    fontWeight: "bold",
    padding: "5px",
    paddingBottom: "10px",
  },
  inforoot: {
    margin: "5px",
  },

  dropdown: {
    marginTop :"50px"
   
  },
  paper: {
    marginRight: theme.spacing(2),
  },
  downmenu: {
    display: 'flex'
    },
}));

function TabPanel(props) {
  const { children, value, index, ...other } = props;

  return (
    <Typography
      component="div"
      role="tabpanel"
      hidden={value !== index}
      id={`scrollable-force-tabpanel-${index}`}
      aria-labelledby={`scrollable-force-tab-${index}`}
      {...other}
    >
      {value === index && <Box p={5}>{children}</Box>}
    </Typography>
  );
}

TabPanel.propTypes = {
  children: PropTypes.node,
  index: PropTypes.any.isRequired,
  value: PropTypes.any.isRequired,
};

function a11yProps(index) {
  return {
    id: `scrollable-force-tab-${index}`,
    "aria-controls": `scrollable-force-tabpanel-${index}`,
  };
}
function HomePage(props) {
  const classes = useStyles();
  const [value, setValue] = useState(null);
  
  const [setting, setSetting] = useState(false);
  const getstate= props.location && props.location.state ? props.location.state : " " ;
  const urlIndex = getQueryParams("tab", props.location && props.location.search ? props.location.search : ""); 
  const urlTabs = urlIndex !== null ? urlIndex : getstate ;
  useEffect ( () => {
    switch(urlTabs){  
      case "database-sync": return setValue(0)
      case "setting": return setValue(1)

      default: return setValue(0)
    }
  }, [urlIndex]);
  
  const handleChange = (event, newValue) => {
    setValue(newValue);
  };


/*Tab Dropdown   */
const [anchorEl, setAnchorEl] = React.useState(null);
  const open = Boolean(anchorEl);

  const handleClick = (event) => {
    setValue(1)
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setSetting(true)
  };

  const settingDropdown = () => {
    handleClose()
    setSetting(true)
  };

/*Tab Dropdown   */



  return (
    <>
    <div className={classes.root} >
      <AppBar position="static" style={{backgroundColor:'#014d88'}}>
        <Tabs
          value={value}
          onChange={handleChange}
          variant="scrollable"
          scrollButtons="on"
          indicatorColor="secondary"
          textColor="inherit"
          aria-label="scrollable force tabs example"
        >
        {/*
          <Tab className={classes.title} label="Central Sync" icon={<CloudSyncIcon style={{ color:'#fff'}}/>} {...a11yProps(0)} />
          <Tab className={classes.title} label="Configuration  " icon={<SettingsIcon />} {...a11yProps(1)}/>
        */}
          <Tab className={classes.title} label="Quick Sync" icon={<RestoreIcon />} {...a11yProps(0)}/>
      </Tabs>
      </AppBar>

      <Menu
        id="fade-menu"
        anchorEl={anchorEl}
        keepMounted
        open={open}
        onClose={handleClose}
        TransitionComponent={Fade}
        className={classes.dropdown}
      >
        <MenuItem onClick={settingDropdown}>Personal Access Token</MenuItem>

      </Menu>
        {/*
        <TabPanel value={value} index={0}>
          <SyncList />
        </TabPanel>
        <TabPanel value={value} index={1}>
              <Setting />
        </TabPanel>
        */}
        <TabPanel value={value} index={0}>
          <Restore />
        </TabPanel>
       {/* <TabPanel value={value} index={3}>
          <Backup />
        </TabPanel> */}
     </div>
    </>
  );
}

export default HomePage;