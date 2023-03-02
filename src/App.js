import React from "react";
import {
  MemoryRouter as Router,
  Switch,
  Route,
} from "react-router-dom";

import { ToastContainer} from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import "./main/webapp/vendor/bootstrap-select/dist/css/bootstrap-select.min.css";
import "./../src/main/webapp/css/style.css";
import 'bootstrap/dist/css/bootstrap.css';
import HomePage from './main/webapp/jsx/components/Dashboard'
//import HomePage from './main/webapp/jsx/ndr/Dashboard'

export default function App() {
  return (
    <Router>
      <div>
      <ToastContainer />
        {/* A <Switch> looks through its children <Route>s and
            renders the first one that matches the current URL. */}
        <Switch>

          <Route path="/">
            <HomePage />
          </Route>

        </Switch>
      </div>
 </Router>
  );
}
