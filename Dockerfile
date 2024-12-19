#build

FROM maven:3.9.9-amazoncorretto-17 as build
WORKDIR /build
COPY . .

RUN mvn clean package -DskipTests

#run

FROM amazoncorretto:17
WORKDIR /app

COPY --from=build ./build/target/*.jar ./form.jar

EXPOSE 8080

EXPOSE 9090

ENV URL_POSTGRES_FULLDEV=jdbc:postgresql://junction.proxy.rlwy.net:21218/railway
ENV USERNAME_POSTGRES_FULLDEV=postgres
ENV PASSWORD_POSTGRES_FULLDEV=iOEmplgbzPuOInPRfRWObqmKAHrBBXBM
ENV JWT_SECRET=fulldev-community
ENV EMAIL_FULLDEV=ericsouza94895@gmail.com
ENV PASSWORD_EMAIL_FULLDEV=koevggbzchqvqjic
ENV CLIENT_ID_FACEBOOK=9113119045416753
ENV CLIENT_SECRET_FACEBOOK=165b6aac4d1a9a8037fa654e5155b248
ENV TZ="America/Sao_Paulo"

ENTRYPOINT java -jar form.jar