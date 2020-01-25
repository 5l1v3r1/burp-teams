var io = require('socket.io').listen(3000);
console.log("Server running...");
io.sockets.on('connection', function(socket) {
	socket.on('set name', function(name){
		socket.name = name;
	});
	socket.on('subscribe', function(teamID, teamName) {
	    console.log(socket.name + ' is ' + 'joining team', teamName);
	    socket.join(teamID);
	});

	socket.on('get users', function(teamID) {
	    var clients = io.sockets.adapter.rooms[teamID].sockets, users = [];
	    for(var clientID in clients) {
	    	users.push(io.sockets.connected[clientID].name);
	    }
	    socket.emit('return users', users.sort());	
	});

	socket.on('send to repeater', function(teamID, host, port, isHttps, message, caption, user){
	    if(user === "All users") {	     
	      socket.broadcast.to(teamID).emit('call send to repeater', {teamID, host, port, isHttps, caption}, message);
	    } else {
	    	var clients = io.sockets.adapter.rooms[teamID].sockets, users = [];
		    for(var clientID in clients) {
		    	if(user === io.sockets.connected[clientID].name) {
		    		io.to(clientID).emit('call send to repeater', {teamID, host, port, isHttps, caption}, message);
		    		break;
				}
		    }
	    }	
	});

	socket.on('send to intruder', function(teamID, host, port, isHttps, message, user){
	    if(user === "All users") {	     
	      socket.broadcast.to(teamID).emit('call send to intruder', {teamID, host, port, isHttps}, message);
	    } else {
	    	var clients = io.sockets.adapter.rooms[teamID].sockets, users = [];
		    for(var clientID in clients) {
		    	if(user === io.sockets.connected[clientID].name) {
		    		io.to(clientID).emit('call send to intruder', {teamID, host, port, isHttps}, message);
		    		break;
				}
		    }
	    }	
	});

	socket.on('send to comparer', function(teamID, message, user){
	    if(user === "All users") {	     
	      socket.broadcast.to(teamID).emit('call send to comparer', {teamID}, message);
	    } else {
	    	var clients = io.sockets.adapter.rooms[teamID].sockets, users = [];
		    for(var clientID in clients) {
		    	if(user === io.sockets.connected[clientID].name) {
		    		io.to(clientID).emit('call send to comparer', {teamID}, message);
		    		break;
				}
		    }
	    }	
	});

	console.log("Connected.");
	socket.on('disconnect', function(){
		console.log('Disconnected.');
	});
});
