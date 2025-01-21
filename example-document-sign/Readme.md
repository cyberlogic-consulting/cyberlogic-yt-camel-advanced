# Apache Camel Application based on a real world example
Code samples demonstrated in video that is not yet published.

## How to build docker image
Docker image can be built by using the following maven command:
```shell script
mvn spring-boot:build-image
```

## How to run tests
Tests are run either inside your IDE or using the following maven command:
```shell script
mvn test
```
To run all tests including integration (need docker client installed), use the following maven command:
```shell script
mvn test -Dtest.integration.enabled=true
```