package io.bugsbunny.dataIngestion.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bugsbunny.data.history.service.PayloadReplayService;
import io.bugsbunny.persistence.MongoDBJsonStore;
import org.mitre.harmony.matchers.ElementPair;
import org.mitre.harmony.matchers.MatcherManager;
import org.mitre.harmony.matchers.MatcherScore;
import org.mitre.harmony.matchers.MatcherScores;
import org.mitre.harmony.matchers.matchers.Matcher;
import org.mitre.schemastore.model.Entity;
import org.mitre.schemastore.model.Schema;
import org.mitre.schemastore.model.SchemaElement;
import org.mitre.schemastore.model.schemaInfo.FilteredSchemaInfo;
import org.mitre.schemastore.model.schemaInfo.HierarchicalSchemaInfo;
import org.mitre.schemastore.model.schemaInfo.SchemaInfo;
import org.mitre.schemastore.model.schemaInfo.model.RelationalSchemaModel;
import org.mitre.schemastore.model.schemaInfo.model.SchemaModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

@ApplicationScoped
public class MapperService {
    private static Logger logger = LoggerFactory.getLogger(MapperService.class);

    @Inject
    private MongoDBJsonStore mongoDBJsonStore;

    @Inject
    private PayloadReplayService payloadReplayService;

    public JsonArray map(String sourceSchema, String destinationSchema, JsonArray sourceData)
    {
        JsonArray result = new JsonArray();
        try
        {
            int size = sourceData.size();
            for(int i=0; i<size; i++)
            {
                JsonObject root = sourceData.get(i).getAsJsonObject();

                HierarchicalSchemaInfo sourceSchemaInfo = this.populateHierarchialSchema(root.toString(),
                        root.toString(), null);
                HierarchicalSchemaInfo destinationSchemaInfo = this.populateHierarchialSchema(root.toString(),
                        root.toString(), null);


                FilteredSchemaInfo f1 = new FilteredSchemaInfo(sourceSchemaInfo);
                f1.addElements(sourceSchemaInfo.getElements(Entity.class));
                FilteredSchemaInfo f2 = new FilteredSchemaInfo(destinationSchemaInfo);
                f2.addElements(destinationSchemaInfo.getElements(Entity.class));
                Map<SchemaElement, Double> scores = this.findMatches(f1, f2, sourceSchemaInfo.getElements(Entity.class));
                //logger.info(scores.toString());
                JsonObject local = this.performMapping(scores, root.toString());
                result.add(local);
            }

            this.payloadReplayService.generateDiffChain(sourceData);

            return result;
        }
        catch(Exception e)
        {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private HierarchicalSchemaInfo createHierachialSchemaInfo(String schemaName)
    {
        Schema schema = new Schema();
        schema.setName(schemaName);

        SchemaModel schemaModel = new RelationalSchemaModel();
        schemaModel.setName(schemaName+"Model");
        SchemaInfo schemaInfo1 = new SchemaInfo(schema, new ArrayList<>(), new ArrayList<>());
        HierarchicalSchemaInfo schemaInfo = new HierarchicalSchemaInfo(schemaInfo1);
        schemaInfo.setModel(schemaModel);

        return schemaInfo;
    }

    private HierarchicalSchemaInfo populateHierarchialSchema(String object, String sourceData, String parent)
    {
        HierarchicalSchemaInfo schemaInfo = this.createHierachialSchemaInfo(object);
        JsonObject jsonObject = JsonParser.parseString(sourceData).getAsJsonObject();

        Set<Map.Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
        for(Map.Entry<String, JsonElement> entry:entrySet)
        {
            String field = entry.getKey();
            JsonElement jsonElement = entry.getValue();

            if(jsonElement.isJsonObject())
            {
                Entity element = new Entity();
                element.setId(field.hashCode());
                element.setName(field);
                element.setDescription(field);
                schemaInfo.addElement(element);
                HierarchicalSchemaInfo fieldInfos = this.populateHierarchialSchema(field,
                        jsonElement.getAsJsonObject().toString(), object);

                ArrayList<SchemaElement> blah = fieldInfos.getElements(Entity.class);
                for(SchemaElement local:blah)
                {
                    schemaInfo.addElement(local);
                }

                continue;
            }
            else if(jsonElement.isJsonArray())
            {
                JsonObject top = jsonElement.getAsJsonArray().get(0).getAsJsonObject();
                HierarchicalSchemaInfo fieldInfos = this.populateHierarchialSchema(field,
                        top.toString(), object);

                ArrayList<SchemaElement> blah = fieldInfos.getElements(Entity.class);
                for(SchemaElement local:blah)
                {
                    schemaInfo.addElement(local);
                }

                continue;
            }
            else
            {
                String objectLocation = parent + "." + object + "." + field;
                Entity element = new Entity();
                element.setId(objectLocation.hashCode());
                element.setName(objectLocation);
                element.setDescription(objectLocation);
                schemaInfo.addElement(element);
            }
        }

        return schemaInfo;
    }

    private JsonObject performMapping(Map<SchemaElement, Double> scores, String json) throws IOException
    {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        //logger.info("*******BINDAASBHIDDU******");

        JsonObject result = new JsonObject();
        Set<Map.Entry<SchemaElement, Double>> entrySet = scores.entrySet();
        for(Map.Entry<SchemaElement, Double> entry: entrySet)
        {
            SchemaElement schemaElement = entry.getKey();
            Double score = entry.getValue();
            String field = schemaElement.getName();
            StringTokenizer tokenizer = new StringTokenizer(field, ".");
            while(tokenizer.hasMoreTokens())
            {
                String local = tokenizer.nextToken();
                if(!jsonObject.has(local))
                {
                    continue;
                }
                result.add(local, jsonObject.get(local));
            }
        }

        return result;
    }

    private Map<SchemaElement,Double> findMatches(FilteredSchemaInfo f1, FilteredSchemaInfo f2,
                                                  ArrayList<SchemaElement> sourceElements)
    {
        Map<SchemaElement, Double> result = new HashMap<>();
        Matcher matcher = MatcherManager.getMatcher(
                "org.mitre.harmony.matchers.matchers.EditDistanceMatcher");
        matcher.initialize(f1, f2);

        MatcherScores matcherScores = matcher.match();
        Set<ElementPair> elementPairs = matcherScores.getElementPairs();
        for (ElementPair elementPair : elementPairs) {
            MatcherScore matcherScore = matcherScores.getScore(elementPair);
            Double score = 0d;
            if(matcherScore != null) {
                score = matcherScore.getTotalEvidence();
            }
            for(SchemaElement schemaElement: sourceElements)
            {
                if(schemaElement.getId() == elementPair.getSourceElement())
                {
                    result.put(schemaElement, score);
                }
            }
        }
        return result;
    }
}
