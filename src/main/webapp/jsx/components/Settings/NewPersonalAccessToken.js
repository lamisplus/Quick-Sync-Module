import React, {useState, useEffect} from 'react';
import { Modal, ModalHeader, ModalBody,Form,FormFeedback,
Row,Col, Card,CardBody, FormGroup, Input, Label} from 'reactstrap';
import Button from '@material-ui/core/Button'
import { makeStyles } from '@material-ui/core/styles'

import { Spinner } from 'reactstrap';
import axios from "axios";
import {  toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { token as token,  url as baseUrl } from "./../../../api";


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



const DatabaseSyn = (props) => {
    const classes = useStyles()
    const [urlHide, setUrlHide] = useState(false);
    const defaultValues = { username: "", password: "", url:"" }
    const [patDetails, setPatDetails] = useState(defaultValues);
    const [saving, setSaving] = useState(false);
    const [serverUrl, setServerUrl] = useState( [])
    const [errors, setErrors] = useState({});


    useEffect(() => {
      ServerUrl()
    }, []);
        ///GET LIST OF Facilities
        async function ServerUrl() {
          axios
              .get(`${baseUrl}sync/remote-urls`,
              { headers: {"Authorization" : `Bearer ${token}`} }
              )
              .then((response) => {
                  setServerUrl(
                      Object.entries(response.data).map(([key, value]) => ({
                          label: value.url,
                          value: value.id,
                        }))
                  );
              })
              .catch((error) => {
  
              });
      
      }
    
    const handleInputChange = e => {
      setPatDetails ({...patDetails,  [e.target.name]: e.target.value});
    }
    /*****  Validation */
    const validate = () => {
    let temp = { ...errors };
    temp.username = patDetails.username
        ? ""
        : "Username is required";
        temp.password = patDetails.password
        ? ""
        : "Password is required";
        temp.url = patDetails.url
        ? ""
        : "Server URL is required";
        setErrors({
            ...temp,
        });
        return Object.values(temp).every((x) => x === "");
    };

    const handleSubmit = (e) => {
      e.preventDefault();
            if (validate()) {      
                    setSaving(true);
                    axios.post(`${baseUrl}sync/remote-access-token`,patDetails,
                     { headers: {"Authorization" : `Bearer ${token}`}},
                    
                    )
                        .then(response => {
                            setSaving(false);
                            props.ServerUrl()
                            toast.success("Token Generated Successful");
                            props.toggleModal()

                        })
                        .catch(error => {
                            setSaving(false);
                            toast.error("Something went wrong");
                        });
            };
        }

      
  return (      
      <div >
         
              <Modal isOpen={props.showModal} toggle={props.toggleModal} className={props.className} size="lg" backdrop={false} backdrop="static">
              <Form >
             <ModalHeader toggle={props.toggleModal}>Personal Access Token </ModalHeader>
                <ModalBody>
                    
                        <Card >
                            <CardBody>
                                <Row >

                                <Col md={12}>
                      <FormGroup>
                      <Label >Server URL * </Label>
                              <Input
                                  type="text"
                                  name="url"
                                  id="url"
                                  value={patDetails.url} 
                                  onChange={handleInputChange}
                                  required
                                  />
                                  {errors.url !=="" ? (
                                                <span className={classes.error}>{errors.url}</span>
                                            ) : "" }   
                      </FormGroup>
                    </Col>                
                    <Col md={12}>
                  <FormGroup>
                  <Label >Username </Label>
                          <Input
                              type="text"
                              name="username"
                              id="username" 
                              value={patDetails.username}
                              onChange={handleInputChange}
                              required
                              />
                        {errors.username !=="" ? (
                                                <span className={classes.error}>{errors.username}</span>
                                            ) : "" }
                  </FormGroup>
                  </Col>
                  <Col md={12}>
                  <FormGroup>
                  <Label >Password </Label>
                          <Input
                              type="password"
                              name="password"
                              id="password" 
                              value={patDetails.password}
                              onChange={handleInputChange}
                              required
                              />
                        {errors.password !=="" ? (
                                                <span className={classes.error}>{errors.password}</span>
                                            ) : "" }
                  </FormGroup>
                  </Col>                    
                </Row>
                    {saving ? <Spinner /> : ""}
                    <br/>              
                        <Button
                            type='submit'
                            variant='contained'
                            color='primary'
                            
                            //startIcon={<SettingsBackupRestoreIcon />}
                            onClick={handleSubmit}
                            
                        >   
                            <span style={{ textTransform: "capitalize " }}>Connect & Generate Token</span>  
                        </Button>
                            </CardBody>
                        </Card> 
                    </ModalBody>
        
                </Form>
      </Modal>
    </div>
  );
}

export default DatabaseSyn;
