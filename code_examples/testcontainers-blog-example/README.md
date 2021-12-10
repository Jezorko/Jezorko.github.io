# Sample project for the _[Testcontainers: a guide to hassle-free integration testing](https://jezorko.github.io/kotlin,/spock,/docker,/testing,/testcontainers/2018/10/08/testcontainers-a-guide-to-hassle-free-integration-testing.html)_ blog article.

## Running tests

First, start the Docker daemon:

```shell
sudo dockerd
```

Then, execute all tests with Maven:

```shell
mvn clean test
```