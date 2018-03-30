
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
	{$project : {
		filePath : 1,
		cut_from: { $indexOfCP: ["$filePath", "/", 7] },
		subPath: { $substrCP: [ "$filePath", 7, 25-7  ] },
		subPathCut: { $substr: [ "$filePath", 7, { $indexOfCP: ["$filePath", "/", 7+1] }-7  ] }
	}},
	{ "$sort" : { "pathName" : 1}}
)
