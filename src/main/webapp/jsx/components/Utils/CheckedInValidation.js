import React from 'react'
import {Alert} from 'reactstrap';

// Show action button if patient is checked in , if not display a message for use to check in patient

export default function CheckInValidation(props){

    return(
        <>
        {props.visitId ? 
            props.actionButton
        : 
        <Alert color='danger'> This patient does not have a current visit. You have to check in to proceed</Alert>
           
    }
    </>
    )
}
