package gallerymine.backend.beans.repository.helpers;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;

public class CustomAggregationOperation implements AggregationOperation {
    private Document operation;

    public CustomAggregationOperation (Document operation) {
        this.operation = operation;
    }

    @Override
    public Document toDocument(AggregationOperationContext context) {
        return new Document("$out", operation);
    }
}
