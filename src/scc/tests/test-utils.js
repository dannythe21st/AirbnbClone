const { faker } = require('@faker-js/faker');
const fs = require('fs')

var users = [];
var images = [];
var houses = [];
var userToDelete;
const locations = ["Lisbon","Porto","Madeira","Azores","Algarve","Braga","Coimbra","Evora","Aveiro","Leiria"]

function loadData() {
	var i
	var basefile
	if( fs.existsSync( '/images')) 
		basefile = '/images/house.'
	else
		basefile =  'images/house.'	
	for( i = 1; i <= 40 ; i++) {
		var img  = fs.readFileSync(basefile + i + '.jpg')
		images.push( img )
	}
	var str;
	if( fs.existsSync('users.data')) {
		str = fs.readFileSync('users.data','utf8')
		users = JSON.parse(str)
	} 
}

loadData();

// All endpoints starting with the following prefixes will be aggregated in the same for the statistics
var statsPrefix = [ ["/rest/media/","GET"],
			["/rest/media","POST"],
			["/rest/user/","GET"],
			["/rest/house/","GET"],
	]

// Function used to compress statistics
global.myProcessEndpoint = function( str, method) {
	var i = 0;
	for( i = 0; i < statsPrefix.length; i++) {
		if( str.startsWith( statsPrefix[i][0]) && method == statsPrefix[i][1])
			return method + ":" + statsPrefix[i][0];
	}
	return method + ":" + str;
}

// Auxiliary function to select an element from an array
Array.prototype.sample = function(){
	   return this[Math.floor(Math.random()*this.length)]
}

// Auxiliary function to select an element from an array
Array.prototype.sampleSkewed = function(){
	return this[randomSkewed(this.length)]
}

// Returns a random value, from 0 to val
function random( val){
	return Math.floor(Math.random() * val)
}

/**
 * Sets the body to an image, when using images.
 */
function uploadImageBody(requestParams, context, ee, next) {
	requestParams.body = images.sample()
	return next()
}

/**
 * Generate data for a new user using Faker
 */
function genNewUser(context, events, done) {
	const first = `${faker.person.firstName()}`
	const last = `${faker.person.lastName()}`
	context.vars.userid = first + "." + last
	context.vars.name = first + " " + last
	context.vars.pwd = `${faker.internet.password()}`
	return done()
}

/** Delete a user. */
function deleteUser(requestParams, context, ee, next) {
    requestParams.url += `/${context.vars.userid}`;
    return next();
}

function deleteReply(context, ee, next) {
	if( response.statusCode >= 200 && response.statusCode < 300)  {
		fs.writeFileSync('users.data', JSON.stringify(users));
	}
	else {
		users.push(userToDelete);
		userToDelete = "";
	}
    return next()
}

function deleteHouse(requestParams, context, ee, next) {

    requestParams.url += `/${context.vars.userid}`;
    return next();
}

/**
 * Select user for deletion
 */
function selectUserDelete(context, events, done) {
	if( users.length > 0) {
		userToDelete = users.pop();
		context.vars.userid = userToDelete.userid
		context.vars.pwd = userToDelete.pwd
        console.log(`id: ${context.vars.userid} pwd: ${context.vars.pwd}`);
	} else {
		delete context.vars.userid
		delete context.vars.pwd
	}
	return done()
}

/**
 * Select user
 */
function selectUserSkewed(context, events, done) {
	if( users.length > 0) {
		let user = users.sample()
		context.vars.userid = user.userid
		context.vars.pwd = user.pwd
        console.log(`id: ${context.vars.userid} pwd: ${context.vars.pwd}`);
	} else {
		delete context.vars.userid
		delete context.vars.pwd
	}
	return done()
}

/**
 * Generate data for a new house using Faker
 */
function genNewHouse(context, events, done) {
	context.vars.name = `${faker.lorem.words({ min: 1, max: 3 })}`
	context.vars.location = locations.sample()
    context.vars.houseid = `${context.vars.name}.${context.vars.location}`
	context.vars.description = `${faker.lorem.paragraph()}`
	context.vars.price = random(500) + 200;
	context.vars.discount = 0;
    context.vars.onDiscount = false;
	if( random(100) == 0) {
		context.vars.discount = random(5) * 10;
        context.vars.onDiscount = true;
    }
	return done()
}

function setQuestion(context, events, next) {
	context.vars.text = faker.lorem.word();
	return next()
}

function setText(context, events, next) {
	context.vars.questionid = `${faker.lorem.words({ min: 1, max: 3 })}`;
	return next()
}

/**
 * Process reply of houses list
 */
function getHousesList(requestParams, response, context, ee, next) {
	if( response.statusCode >= 200 && response.statusCode < 300 && response.body.length > 0)  {
		houses = JSON.parse( response.body);
	}
    return next()
}

function searchAvailableHouses(context, ee, done) {
	context.vars.location = locations.sample();
	var iDate = faker.date.soon({days: 20});
	var fDate = faker.date.soon({day:4, refDate:iDate});
	context.vars.initialDateISO = iDate.toISOString();
	context.vars.finalDateISO = fDate.toISOString();
	context.vars.initialDate = iDate;
	context.vars.finalDate = fDate;
	return done();
}

function selectHouse(context, events, done) {
	if( houses.length > 0) {
		let house = houses.sample()
        context.vars.houseid = house.id;
	} else {
		delete context.vars.houseid
	}
	return done()
}


/**
 * Process reply for of new users to store the id on file
 */
function genNewUserReply(requestParams, response, context, ee, next) {
	if( response.statusCode >= 200 && response.statusCode < 300 && response.body.length > 0)  {
		const u = {
            "userid" : context.vars.userid,
            "pwd" : context.vars.pwd
        }
		users.push(u)
		fs.writeFileSync('users.data', JSON.stringify(users));
	}
    return next()
}

module.exports = {
    deleteUser,
    deleteHouse,
    uploadImageBody,
    genNewUser,
    genNewUserReply,
    genNewHouse,
    getHousesList,
    selectUserSkewed,
    selectHouse,
    setQuestion,
	searchAvailableHouses,
	selectUserDelete,
	deleteReply,
	setText
};
