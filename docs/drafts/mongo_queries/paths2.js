
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
	{ "$group": { 
            "_id": { 
                    "$substr": [ 
                        "$filePath", 
                        7,
                        { "$indexOfCP": [ "$filePath", "/", 7 ] }-7
                    ]
            },
			cut_from: { $indexOfCP: ["$filePath", "/", 7] }
//            ,count: { $sum: 1 }
        }},
        { "$sort" : { "_id" : 1}}
)
