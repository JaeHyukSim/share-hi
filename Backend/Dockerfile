FROM node
WORKDIR /usr/src/app
COPY package*.json ./

RUN npm install

COPY . .

EXPOSE 8089

CMD ["node", "./bin/www.js"]

