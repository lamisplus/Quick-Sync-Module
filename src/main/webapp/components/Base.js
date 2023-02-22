import React from 'react'
import Container from '@mui/material/Container';
import Upload from './Upload'
import Table from './Table'

function Base() {
  return (
    <Container maxWidth>
        <Upload />
        <Table />
    </Container>
  )
}

export default Base