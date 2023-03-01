import React, {useState} from 'react';
import Button from '@material-ui/core/Button';
import Card from '@material-ui/core/Card';
import CardActions from '@material-ui/core/CardActions';
import CardContent from '@material-ui/core/CardContent';
import CardHeader from '@material-ui/core/CardHeader';
import Grid from '@material-ui/core/Grid';
import BackupIcon from '@material-ui/icons/Backup';
import Typography from '@material-ui/core/Typography';
import Link from '@material-ui/core/Link';
import { makeStyles } from '@material-ui/core/styles';
import Container from '@material-ui/core/Container';
import RestoreIcon from '@material-ui/icons/Restore';
import SettingsBackupRestoreIcon from '@material-ui/icons/SettingsBackupRestore';
import DatabaseRestore from './Restore';
import DataBaseUp from './Backup';
import DatabaseSyn from './Sync/SyncModal'
import { useHistory } from "react-router-dom";



const useStyles = makeStyles((theme) => ({
  '@global': {
    ul: {
      margin: 0,
      padding: 0,
      listStyle: 'none',
    },
  },
  appBar: {
    borderBottom: `1px solid ${theme.palette.divider}`,
  },
  toolbar: {
    flexWrap: 'wrap',
  },
  toolbarTitle: {
    flexGrow: 1,
  },
  link: {
    margin: theme.spacing(1, 1.5),
  },
  heroContent: {
    padding: theme.spacing(8, 0, 6),
  },
  cardHeader: {
    backgroundColor:
      theme.palette.type === 'light' ? theme.palette.grey[200] : theme.palette.grey[700],
  },
  cardPricing: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'baseline',
    marginBottom: theme.spacing(2),
  },
  footer: {
    borderTop: `1px solid ${theme.palette.divider}`,
    marginTop: theme.spacing(8),
    paddingTop: theme.spacing(3),
    paddingBottom: theme.spacing(3),
    [theme.breakpoints.up('sm')]: {
      paddingTop: theme.spacing(6),
      paddingBottom: theme.spacing(6),
    },
  },
}));



export default function ExportImport() {
  const classes = useStyles();
  const [modal3, setModal3] = useState(false)//modal to View Backup
  const togglemodal3 = () => setModal3(!modal3)
  const [modal2, setModal2] = useState(false)//modal to View Restore
  const togglemodal2 = () => setModal2(!modal2)
  const [modal, setModal] = useState(false)//modal to View Restore
  const togglemodal = () => setModal(!modal)
  const [collectmodal, setcollectmodal] = useState([])//to collect array of datas into the modal and pass it as props
  let history = useHistory();
  function DatabaseModal (){
    setcollectmodal();
    setModal3(!modal3) 
  }

  function DatabaseSynModal (){
    setModal(!modal) 
  }
  function syncLink(){
      history.push('/sync')
  }
  function DatabaseRestoreModal (){
    setModal2(!modal2) 
  }
  const  settings =()=> {        
        history.push('/settings')
    }

  return (
    <React.Fragment>  
      <Container maxWidth="sm" component="main" className={classes.heroContent}>
        
      </Container>
      {/* End hero unit */}
      <Container maxWidth="md" component="main">
      <Button
            variant="contained"
            color="primary"
            className=" float-right mr-1"
            //startIcon={<FaUserPlus />}
            onClick={settings}
          >
            <span style={{ textTransform: "capitalize" }}>Setting</span>
        </Button>
        <Grid container spacing={5} alignItems="flex-end">
         
           <Grid item  xs={12} sm={12} md={4}>
              <Card>
                <CardHeader
                  title='Database BackUp'
                  titleTypographyProps={{ align: 'center' }}
                  className={classes.cardHeader}
                />
                <CardContent>
                  <div className={classes.cardPricing}>
                    <Typography component="h6" variant="h6" color="textPrimary">
                       <BackupIcon  style={{ fontSize: 60 }}/> 
                    </Typography>
                    
                  </div>
                  
                </CardContent>
                <CardActions>
                  <Button fullWidth variant="contained" color="primary" onClick={() => DatabaseModal()}>
                    BackUp
                  </Button>
                </CardActions>
              </Card>
            </Grid>
            <Grid item  xs={12} sm={12} md={4}>
              <Card>
                <CardHeader
                  title='Database Restore'
                  titleTypographyProps={{ align: 'center' }}
                  className={classes.cardHeader}
                />
                <CardContent>
                  <div className={classes.cardPricing}>
                    <Typography component="h6" variant="h6" color="textPrimary">
                    <RestoreIcon  style={{ fontSize: 60 }}/> 
                    </Typography>
                    
                  </div>
                  
                </CardContent>
                <CardActions>
                  <Button fullWidth variant="contained" onClick={()=>DatabaseRestoreModal()}>
                    Restore
                  </Button>
                </CardActions>
              </Card>
            </Grid>
            <Grid item  xs={12} sm={12} md={4}>
              <Card>
                <CardHeader
                  title='Database Sync'
                  titleTypographyProps={{ align: 'center' }}
                  className={classes.cardHeader}
                />
                <CardContent>
                  <div className={classes.cardPricing}>
                    <Typography component="h6" variant="h6" color="textPrimary">
                      <SettingsBackupRestoreIcon  style={{ fontSize: 60 }}/> 
                    </Typography>
                    
                  </div>
                 
                </CardContent>
                <CardActions>
                
                  <Button fullWidth variant="contained" color="primary" onClick={()=>syncLink()}>
                  
                    Sync
                   
                  </Button>
                </CardActions>
              </Card>
            </Grid>
        </Grid>
      </Container>
      <DataBaseUp modalstatus={modal3} togglestatus={togglemodal3} /> 
      <DatabaseRestore modalstatus={modal2} togglestatus={togglemodal2} />
      <DatabaseSyn modalstatus={modal} togglestatus={togglemodal}  />
    </React.Fragment>
  );
}