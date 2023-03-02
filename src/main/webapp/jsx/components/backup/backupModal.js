import React, {useState, useEffect} from 'react';
import { Modal, ModalHeader, ModalBody,Form,FormFeedback,
Row,Col, Card,CardBody} from 'reactstrap';
import MatButton from '@material-ui/core/Button'
import { makeStyles } from '@material-ui/core/styles'
import BackupIcon from '@material-ui/icons/Backup';
import CancelIcon from '@material-ui/icons/Cancel'
import { Alert } from 'reactstrap';
import { Spinner } from 'reactstrap';
import axios from "axios";



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


const url = 'http://lamisplus.org/base-module/api/encounters/87cb9bc7-ea0d-4c83-a70d-b57a5fb7769e/%7BdateStart%7D/%7BdateEnd%7D'
const token = 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBsYW1pc3BsdXMub3JnIiwiYXV0aCI6IlN1cGVyIEFkbWluIiwibmFtZSI6IkVtbWEgSXNvYm9rbyIsImV4cCI6MTYxODQ5MTczOX0.bOcbjIjEdEyc_S7VfJrpgSaajHG2e_c-fAYwwGiucb2-9DXgQruW9lnuBIJzL0CZnTdevOcAsAYVy61jlFs9Kw'

const DataBaseUp = (props) => {
    const classes = useStyles()
    const BackupProcessing = e => {
     alert ('Backup Processing. Please Wait...')
    props.togglestatus();
                    
          
  }


      
  return (      
      <div >
         
              <Modal isOpen={props.modalstatus} toggle={props.togglestatus} className={props.className} size="lg">

            <ModalHeader toggle={props.togglestatus}>Database Backup</ModalHeader>
                <ModalBody>
                    
                        <Card >
                            <CardBody>
                                <Row >
                                    <Col md={12} >
                                        <Alert color="dark" style={{backgroundColor:'#9F9FA5', color:"#000" , fontWeight: 'bolder', fontSize:'14px'}}>
                                            <p style={{marginTop: '.7rem' }}>
                                                Info : &nbsp;&nbsp;&nbsp;<span style={{ fontWeight: 'bolder'}}>{'Are you Sure want to continue'}</span>
                                                &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                                               
                                            </p>

                                        </Alert>
                                    </Col>
                                    
                                    <Col md={6}>
                                                                             
                                      </Col>                   
                                  </Row>
                                      <br/>
                                      
                                      <br/>
                                      
                                          <MatButton
                                              type='submit'
                                              variant='contained'
                                              color='primary'
                                              className={classes.button}
                                              startIcon={<BackupIcon />}
                                              onClick={()=>BackupProcessing()}
                                             
                                          >   
                                              BackUp
                                          </MatButton>
                                           
                                          <MatButton
                                              variant='contained'
                                              color='default'
                                              onClick={props.togglestatus}
                                              className={classes.button}
                                              startIcon={<CancelIcon />}
                                          >
                                              Cancel
                                          </MatButton>
                            </CardBody>
                        </Card> 
                    </ModalBody>
        
      </Modal>
    </div>
  );
}

export default DataBaseUp;
