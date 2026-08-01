FROM knokkogator/kci:0.1.8-mc26 AS build_base
RUN mkdir /custom-items
WORKDIR /custom-items
COPY gradlew ./gradlew
RUN dos2unix ./gradlew
COPY gradle ./gradle
COPY kci-nms ./kci-nms
COPY bit-helper ./bit-helper
COPY shared-code ./shared-code

FROM build_base AS build_plugin
COPY plug-in ./plug-in
COPY custom-recipes ./custom-recipes
COPY .github/plug-in/build.gradle ./
COPY .github/plug-in/settings.gradle ./
RUN ./gradlew shadowJar --no-daemon

FROM build_base AS build_nms
RUN apk add zip
COPY kci-nms13plus ./kci-nms13plus
COPY kci-nms16plus ./kci-nms16plus
COPY kci-nms18plus ./kci-nms18plus
COPY kci-nms21plus ./kci-nms21plus
COPY kci-nms26plus ./kci-nms26plus
COPY kci-nms26 ./kci-nms26
COPY .github/mc26/build.gradle ./
COPY .github/mc26/settings.gradle ./
RUN ./gradlew build --no-daemon
RUN mkdir combined
RUN unzip -d combined -o kci-nms13plus/build/libs/kci-nms13plus.jar
RUN unzip -d combined -o kci-nms16plus/build/libs/kci-nms16plus.jar
RUN unzip -d combined -o kci-nms18plus/build/libs/kci-nms18plus.jar
RUN unzip -d combined -o kci-nms21plus/build/libs/kci-nms21plus.jar
RUN unzip -d combined -o kci-nms26plus/build/libs/kci-nms26plus.jar
RUN unzip -d combined -o kci-nms26/build/libs/kci-nms26.jar
COPY --from=build_plugin /custom-items/plug-in/build/libs/plug-in-all.jar ./plug-in-raw.jar
RUN unzip -d combined -o plug-in-raw.jar
WORKDIR /custom-items/combined
RUN zip -r CustomItems.jar *

FROM knokkogator/kci:0.1.8-mc26 AS prepared

RUN mkdir /server
RUN wget -O /server/server.jar https://fill-data.papermc.io/v1/objects/3ab7536642d04c504a06fe43174b8a94f8c5f25d5847d4672212413f6e54b906/paper-26.2-87.jar
RUN echo "3ab7536642d04c504a06fe43174b8a94f8c5f25d5847d4672212413f6e54b906  /server/server.jar" | sha256sum -c -
WORKDIR /server

RUN mkdir plugins
RUN echo eula=true > eula.txt
EXPOSE 25565

FROM prepared AS generated
RUN echo stop | java -jar server.jar
COPY --from=build_nms /custom-items/combined/CustomItems.jar ./plugins/CustomItems.jar
