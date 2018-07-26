db.picture.aggregate([
    {
        "$match": {
            "$and": [
                {
                    "filePath": {
                        "$regex": "^[^\\/]+/[^\\/]+$"
                    }
                }
            ]
        }
    },
    {
        "$project": {
            "filePath": 1,
            "name": {
                "$arrayElemAt": [
                    {
                        "$split": [
                            "$filePath",
                            "/"
                        ]
                    },
                    0
                ]
            }
        }
    },
    {
        "$group": {
            "_id": {
                "name": "$name",
                "filePath": "$filePath"
            },
            "count": {
                "$sum": 1
            }
        }
    },
    {
        "$project": {
            "name": "$_id.name",
            "filesCount": "$count",
            "fullPath": "$_id.filePath"
        }
    },
    {
        "$group": {
            "_id": {
                "name": "$name"
            },
            "filesCount": {
                "$sum": "$filesCount"
            },
            "foldersCount": {
                "$sum": 1
            }
        }
    },
    {
        "$sort": {
            "name": -1
        }
    },
    {
        "$skip": 0
    },
    {
        "$limit": 1000
    }
])
{ "_id" : { "name" : "test" }, "filesCount" : 1, "foldersCount" : 1 }