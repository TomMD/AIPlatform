package io.bugsbunny.persistence;

import com.google.gson.*;

import io.bugsbunny.dataScience.service.PackagingService;
import io.bugsbunny.endpoint.SecurityToken;
import io.bugsbunny.endpoint.SecurityTokenContainer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class MongoDBJsonStoreTests {
    private static Logger logger = LoggerFactory.getLogger(MongoDBJsonStoreTests.class);

    private Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @Inject
    private MongoDBJsonStore mongoDBJsonStore;

    @Inject
    private PackagingService packagingService;

    @Inject
    private SecurityTokenContainer securityTokenContainer;

    @BeforeEach
    public void setUp() throws Exception
    {
        String securityTokenJson = IOUtils.toString(Thread.currentThread().getContextClassLoader().
                        getResourceAsStream("oauthAgent/token.json"),
                StandardCharsets.UTF_8);
        SecurityToken securityToken = SecurityToken.fromJson(securityTokenJson);
        this.securityTokenContainer.getTokenContainer().set(securityToken);
    }

    @AfterEach
    public void tearDown()
    {

    }

    @Test
    public void testIngestionData() throws Exception
    {
        String json = IOUtils.toString(Thread.currentThread().getContextClassLoader().
                        getResourceAsStream("people.json"),
                StandardCharsets.UTF_8);

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        List<JsonObject> objects = Arrays.asList(new JsonObject[]{jsonObject});

        this.mongoDBJsonStore.storeIngestion(objects);
        JsonObject storedJson = this.mongoDBJsonStore.getIngestion("1");

        logger.info("*******");
        logger.info(storedJson.toString());
        logger.info("*******");
        assertEquals("James", storedJson.get("firstname").getAsString());
    }



    @Test
    public void testDevModelData() throws Exception
    {

    }

    @Test
    public void testDataHistoryStorage() throws Exception
    {

    }

    @Test
    public void testStoreDataSetRealDataForEval() throws Exception
    {
        String csv = IOUtils.toString(
                Thread.currentThread().getContextClassLoader().getResourceAsStream("dataScience/saturn_data_eval.csv"),
                StandardCharsets.UTF_8);
        logger.info(csv);

        long oid = this.mongoDBJsonStore.storeDataSet("csv", "training", csv);

        JsonObject dataSet = this.mongoDBJsonStore.readDataSet(oid);
        String csvData = dataSet.get("data").getAsString();
        long storedOid = dataSet.get("dataSetId").getAsLong();
        logger.info(""+storedOid);
        assertEquals(oid, storedOid);
        assertEquals(csv, csvData);
    }

    @Test
    public void testRollOverToTraningDataSets() throws Exception
    {
        String modelPackage = IOUtils.resourceToString("dataScience/aiplatform-model.json", StandardCharsets.UTF_8,
                Thread.currentThread().getContextClassLoader());

        JsonObject liveModelDeployedJson = this.packagingService.performPackaging(modelPackage);
        long modelId = liveModelDeployedJson.get("modelId").getAsLong();

        String data = IOUtils.resourceToString("dataScience/saturn_data_eval.csv", StandardCharsets.UTF_8,
                Thread.currentThread().getContextClassLoader());
        JsonObject input = new JsonObject();
        input.addProperty("modelId", modelId);
        input.addProperty("format", "csv");
        input.addProperty("data", data);
        Response response = given().body(input.toString()).when().post("/dataset/storeEvalDataSet/").andReturn();
        logger.info("************************");
        logger.info("ModelId: "+modelId);
        logger.info(response.statusLine());
        response.body().prettyPrint();
        logger.info("************************");
        assertEquals(200, response.getStatusCode());
        long dataSetId = JsonParser.parseString(response.body().asString()).getAsJsonObject().get("dataSetId").getAsLong();


        JsonObject rolledOverDataSetIds = this.mongoDBJsonStore.rollOverToTraningDataSets(modelId);
        logger.info(rolledOverDataSetIds.toString());

        //Assert
        List<Long> dataSetIds = new ArrayList<>();
        JsonArray array = rolledOverDataSetIds.getAsJsonArray("rolledOverDataSetIds");
        Iterator<JsonElement> iterator = array.iterator();
        while(iterator.hasNext())
        {
            dataSetIds.add(iterator.next().getAsLong());
        }
        assertTrue(dataSetIds.contains(dataSetId));
    }
}
