FROM node:20

WORKDIR /usr/src/app

COPY . ./tests

RUN npm install -g artillery -y \
    && npm install @faker-js/faker --save-dev -y\
    && artillery run ./tests/load-data.yml \
    && artillery run --output run-01.json ./tests/tests.yml \
    && artillery report run-01.json
