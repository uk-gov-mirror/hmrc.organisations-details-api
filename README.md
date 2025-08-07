# organisations-details-api

This API allows users to check HMRC records to find information about an organisation.

### Documentation

The documentation on [confluence](https://confluence.tools.tax.service.gov.uk/display/MDS/Development+space) includes:

- Configuration driven management of data and scopes
- Scope driven query strings for Integration Framework (IF)
- Caching strategy to alleviate load on backend systems

Please ensure you reference the OGD Data Item matrix to ensure the right data items are mapped and keep this document up
to date if further data items are added.
(Current version V1.1)

### Running the service

Follow the process for [matching](https://github.com/hmrc/organisations-matching-api) to generate a matchId.

Ensure mongo and service manager (`sm2 --start OVHO -r`) are running.

The service can be run on port 9656 with:

    sbt run

Headers, endpoints, and example request bodies can be found in the documentation
on [DevHub](https://developer.qa.tax.service.gov.uk/api-documentation/docs/api/service/organisations-details-api/1.0).

### Running tests

Run all the tests with coverage report:

    sbt clean compile coverage test it:test component:test coverageReport

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
