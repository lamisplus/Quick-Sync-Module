import React, {useState, useEffect} from 'react'
import axios from "axios";

import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { token as token,  url as baseUrl } from "./../../../api";
import SettingList from './SettingList' 



const TabExampleMenuPositionRight = (props) => {

    const defaultValues = { username: "", password: "", url:"" }
    const [patDetails, setPatDetails] = useState(defaultValues);
    const [saving, setSaving] = useState(false);
    const [serverUrl, setServerUrl] = useState( [])



    useEffect(() => {
      ServerUrl()
    }, []);
        ///GET LIST OF Facilities
        async function ServerUrl() {
          axios
              .get(`${baseUrl}sync/remote-urls`,
              //{ headers: {"Authorization" : `Bearer ${token}`} }
              )
              .then((response) => {
                  console.log(response.data)
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
      console.log(patDetails)
    }

    const handleSubmit = (e) => {
      console.log(patDetails)
      e.preventDefault();      
      setSaving(true);
      axios.post(`${baseUrl}sync/remote-access-token`,
     // { headers: {"Authorization" : `Bearer ${token}`}},
      patDetails
      )
                  .then(response => {
                      setSaving(false);
                      toast.success("Token Generated Successful");

                  })
                  .catch(error => {
                      setSaving(false);
                      toast.error("Something went wrong");
                  });
    };




  return (    
          
          <SettingList />
        )

}

export default TabExampleMenuPositionRight