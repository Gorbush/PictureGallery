
db.source.aggregate(
  	{ 
  	  "$match" : { 
  	    "$and" : [ 
  	 	  { 
  		    "filePath" : { "$regex" : "^_Parse\/.*/[^/]*$"}
	  	  }
  	  	]
  	  }
  	},
	{ 
	  "$group": {
            "_id":	{   "$arrayElemAt": [ { "$split": [ "$filePath", "/" ] }, 1]   }
            ,count: { $sum: 1 }
        }
	},
	{ "$sort" : { "_id" : 1}}
)
