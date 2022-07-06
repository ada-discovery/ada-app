# Ada App [![version](https://img.shields.io/badge/version-0.10.1-green.svg)](https://ada-discovery.github.io) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0) [![Build Status](https://travis-ci.com/ada-discovery/ada-app.svg?branch=master)](https://travis-ci.com/ada-discovery/ada-app)

<img src="https://ada-discovery.github.io/images/logo.png" width="450px" alt="A Discovery Analytics">

This source code repo represents the base of Ada Discovery Analytics platform. It contains three components:

##### *Ada Server*

Server part provides several headless functionality, in particular:

* Domain classes with JSON formatters.
* Persistence layer with convenient repo abstractions for Mongo, Elastic Search, and Apache Ignite. 
* WS clients to call REDCap, Synapse, and eGaIT REST services.
* Data set importers and transformations.
* Stats calculators with Akka streaming support.
* Machine learning service providing various classification, regression, and clustering routines backed by Apache Spark.

##### *Ada Web*

This is a web part of the platform serving as a visual manifestation of [Ada server](https://github.com/ada-discovery/ada-server).  In a nutshell, _Ada web_ consists of controllers with actions whose results are rendered by views as actual presentation endpoints produced by an HTML templating engine. It is implemented by using [Play](https://www.playframework.com) Framework, a popular web framework for Scala built on asynchronous non-blocking processing.

Ada's main features include a visually pleasing and intuitive web UI for an interactive data set exploration and filtering, and configurable views with widgets presenting various statistical results, such as, distributions, scatters, correlations, independence tests, and box plots.  Ada facilitates robust access control through LDAP authentication and an in-house user management with fine-grained permissions backed by [Deadbolt](http://deadbolt.ws) library.

##### *Ada Web NCER-PD* 

Custom extension serving NCER-PD project in Luxembourg, primarily a batch order request plugin.


#### Installation

There are essentially two ways how to install a full-stack _Ada Web_:

- Install all the components including Mongo and Elastic Search _manually_, which gives a full control and all configurability options at the expense of moderate installation and maintenance effort. The complete guides are available for  [Linux](Installation_Linux.md) and [MacOS](Installation_MacOS.md).

#### Development

[Here](development.md) are some guidelines for developers:


#### Using the Libs

If you want to use *Ada Server* in your own project all you need is *Scala 2.11*. To pull the library you have to add the following dependency to *build.sbt*

```
"org.adada" %% "ada-server" % "0.10.1"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-server_2.11</artifactId>
    <version>0.10.1</version>
</dependency>
```

Similarly, if instead of installing a stand alone Ada app, you want to use the _Ada Web_ libraries in your project, you can do so by adding the following dependencies in *build.sbt* (be sure the Scala compilation version is *2.11*)

```
"org.adada" %% "ada-web" % "0.10.1",
"org.adada" %% "ada-web" % "0.10.1" classifier "assets"
```

Alternatively if you use maven  your *pom.xml* has to contain

```
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-web_2.11</artifactId>
    <version>0.10.1</version>
</dependency>
<dependency>
    <groupId>org.adada</groupId>
    <artifactId>ada-web_2.11</artifactId>
    <version>0.10.1</version>
    <classifier>assets</classifier>
</dependency>
```

#### License

The project and all its source code (i.e., everything belonging to *Ada Server*, *Ada Web* and *Ada Web NCER* subprojects) is distributed under the terms of the <a href="https://www.apache.org/licenses/LICENSE-2.0.txt">Apache 2.0 license</a>.

Note that if you wish to use the [Highcharts](https://www.highcharts.com)-based widget engine in Ada, along with or as a replacement for the default one implemented using [Plotly](https://plotly.com/) library, you can do so by using/extending [Ada Web Highcharts project](https://github.com/ada-discovery/ada-web-highcharts), which is however distributed under the <a href="https://creativecommons.org/licenses/by-nc/3.0/">CC BY-NC 3.0 license</a>.   

#### Acknowledgement and Support

Development of this project has been significantly supported by

* an FNR Grant (2015-2019, 2019-ongoing): *National Centre of Excellence in Research on Parkinson's Disease (NCER-PD)*: Phase I and Phase II

* a one-year MJFF Grant (2018-2019): *Scalable Machine Learning And Reservoir Computing Platform for Analyzing Temporal Data Sets in the Context of Parkinson’s Disease and Biomedicine*

<br/>

<a href="https://wwwen.uni.lu/lcsb"><img src="https://ada-discovery.github.io/images/logos/logoLCSB-long-230x97.jpg" width="184px"></a>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;<a href="https://www.fnr.lu"><img src="https://ada-discovery.github.io/images/logos/fnr_logo-350x94.png" width="280px"></a>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;<a href="https://www.michaeljfox.org"><img src="https://ada-discovery.github.io/images/logos/MJFF-logo-resized-300x99.jpg" width="240px"></a>
