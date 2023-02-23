import React, { useState, useEffect } from "react";
import axios from "axios";
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { FormControlLabel } from "@mui/material";
import { TextField } from '@mui/material';
import Checkbox from '@mui/material/Checkbox';
import FormGroup from '@mui/material/FormGroup';
import Paper from '@mui/material/Paper';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import FileUploadIcon from '@mui/icons-material/FileUpload';
import {token, url as baseUrl} from '../utils/api';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';

function Upload() {
    const [formValues, setFormValues] = useState({
        organisationUnitId: "",
        dateRange: "",
        htsData: "",
        patientData: "",
        biometricsData: ""
    })
    const [facilities, setFacilities] = useState([]);
    // const [HtsData, setHtsData] = useState(false);
    // const [BiometricsData, setBiometricsData] = useState(false);
    // const [PatientData, setPatientData] = useState(false);

    useEffect(() => {
        Facilities()
    }, []);

    const Facilities =()=>{
    axios
        .get(`${baseUrl}account`,
            { headers: {"Authorization" : `Bearer ${token}`} }
        )
        .then((response) => {
        console.log(response.data);
            setFacilities(response.data.applicationUserOrganisationUnits);
        })
        .catch((error) => {
        //console.log(error);
        });

    }

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        //console.log(name, value)
        setFormValues({
            ...formValues,
            [name]: value,
        });
    }

    const handleSubmit = (event) => {
        event.preventDefault();
        console.log(formValues);
    };
 
    return (
        <>
        <br />
        <br />
        <Grid container component="main">
            <Grid item xs={8} sm={8} md={8} component={Paper} elevation={3}>
                <Box
                    sx={{
                    my: 3,
                    mx: 3
                    }}
                >
                    <Typography variant="h6" gutterBottom>
                        Export Data
                    </Typography>
                    <form onSubmit={handleSubmit}>
                        <Box sx={{display: 'flex',
                            flexDirection: 'row'}}>
                            <Grid container spacing={2}>
                                <Grid item xs={12} sm={6}>
                                <FormControl fullWidth>
                                <InputLabel id="demo-simple-select-label">Facility</InputLabel>
                                    <Select
                                        labelId="demo-simple-select-label"
                                        id="demo-simple-select"
                                        value={formValues.organisationUnitId}
                                        label="Facility"
                                        onChange={handleInputChange}
                                        name="organisationUnitId"
                                        >
                                        {
                                            facilities.map((value) => (
                                                <MenuItem key={value.id} value={value.organisationUnitId}>{value.organisationUnitName}</MenuItem>
                                            ))
                                        }
                                    </Select>
                                </FormControl>
                                </Grid>
                                <Grid item xs={12} sm={6}>
                                    <TextField
                                        required
                                        fullWidth
                                        id="email"
                                        label="Email Address"
                                        name="email"
                                    />
                                </Grid>
                            </Grid>
                        </Box>
                        <Box>
                            <FormGroup row>
                                <FormControlLabel control={<Checkbox onChange={handleInputChange} />} label="Generate Patient Data" />
                                <FormControlLabel control={<Checkbox onChange={handleInputChange} />} label="Generate HTS Data" />
                                <FormControlLabel control={<Checkbox onChange={handleInputChange} />} label="Generate Biometric Data" />
                            </FormGroup>
                        </Box>
                        <Box>
                            <Button
                                    type="submit"
                                    variant="contained"
                                    sx={{ mt: 2, mb: 2 }}
                                >
                                    Generate JSON
                            </Button>
                        </Box>  
                    </form>
                </Box> 
            </Grid> 
            <Grid item xs={4} sm={4} md={4} component={Paper} elevation={3}>
                <Box
                    sx={{
                    my: 3,
                    mx: 3
                    }}
                >
                    <Typography variant="h6" gutterBottom>
                        Import Data
                    </Typography>
                    <Stack direction="row" alignItems="center" spacing={2}>
                        <Button variant="contained" component="label">
                            <FileUploadIcon/> Upload JSON File
                            <input hidden accept=".json" multiple type="file" />
                        </Button>
                    </Stack>
                </Box>
            </Grid> 
        </Grid> 
        </>
    );
}

export default Upload;
