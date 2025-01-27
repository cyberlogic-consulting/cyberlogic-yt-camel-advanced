# Apache Camel Application based on a real world example
Code samples demonstrated in video that is not yet published.

## How to run
To run the example, first build docker image of the application (need docker client installed):
```shell script
mvn spring-boot:build-image
```
Then, application and all required systems (sftp, postgres, artemisMQ) can be run with the following command:
```shell script
docker compose -f ./docker/docker-compose.yml up
```
Then, the following systems will be accessible:
- sftp - is forwarded on localhost:22, can be accessed with login/password docsend_sftp_user/docsend_sftp_password. You can upload a file to the /home/docsend_sftp_user/documents folder and see how it is processed by the application.
- pgadmin - is forwarded on [localhost:8888](http://localhost:8888/browser/), can connect to it in a browser and observe how document processing is logged into the database.
- artemisMQ - is forwarded on [localhost:8161](http://localhost:8161/console/artemis/), can connect to it in browser and see how messages appear in the ```send-document-request``` queue.

## How to run tests
Tests are run either inside your IDE or using the following maven command:
```shell script
mvn test
```
To run all tests including integration (need docker client installed), use the following maven command:
```shell script
mvn test -Dtest.integration.enabled=true
```