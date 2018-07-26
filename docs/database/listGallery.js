
db.picture.aggregate([
    {
        "$match": {
            "$and": [
                {
                    "filePath": {
                        "$regex": "^[^/]*$"
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
        "$group": {
            "_id": null,
            "count": {
                "$sum": "$count"
            }
        }
    },
    {
        "$project": {
            "name": "$_id.name",
            "filesCount": "$count",
            "fullPath": "$_id.filePath"
        }
    }
])