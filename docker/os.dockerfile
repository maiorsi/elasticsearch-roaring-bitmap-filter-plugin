FROM opensearchproject/opensearch:3.3.2

# Copy plugin distribution into the image and install it.
# Expects that `./gradlew :os:distZip` has produced
# os/build/distributions/os-rb-plugin.zip on the host.
ARG PLUGIN_ZIP=os/build/distributions/os-rb-plugin.zip

COPY ${PLUGIN_ZIP} /tmp/os-rb-plugin.zip

RUN opensearch-plugin install file:///tmp/os-rb-plugin.zip --batch

# Use the non-root user provided by the image
USER 1000:0

EXPOSE 9200 9300 9600 9650
