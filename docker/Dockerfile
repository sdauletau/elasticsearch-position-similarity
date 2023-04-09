ARG ES_VERSION
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION} AS builder-image

WORKDIR /elasticsearch-position-similarity
COPY ../ .

ENV JAVA_HOME /usr/share/elasticsearch/jdk
ENV PATH $JAVA_HOME/bin:$PATH
RUN ./gradlew clean build
RUN ./scripts/install-plugin.sh

ARG ES_VERSION
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

COPY --from=builder-image /usr/share/elasticsearch/plugins/elasticsearch-position-similarity/ /usr/share/elasticsearch/plugins/elasticsearch-position-similarity/
COPY --from=builder-image /elasticsearch-position-similarity/examples/ /elasticsearch-position-similarity/examples/

WORKDIR /elasticsearch-position-similarity
