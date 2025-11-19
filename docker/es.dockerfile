FROM docker.elastic.co/elasticsearch/elasticsearch:9.2.0

# Copy plugin distribution into the image and install it.
# Expects that `./gradlew :es:distZip` has produced
# es/build/distributions/es-rb-plugin.zip on the host.
ARG PLUGIN_ZIP=es/build/distributions/es-rb-plugin.zip

COPY ${PLUGIN_ZIP} /tmp/es-rb-plugin.zip

RUN elasticsearch-plugin install file:///tmp/es-rb-plugin.zip --batch

# Use a non-root user when possible (official image handles this)
USER elasticsearch

EXPOSE 9200 9300

CMD ["/usr/local/bin/docker-entrypoint.sh"]
