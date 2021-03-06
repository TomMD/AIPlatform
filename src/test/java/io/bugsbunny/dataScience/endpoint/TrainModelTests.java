package io.bugsbunny.dataScience.endpoint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bugsbunny.data.history.service.PayloadReplayService;
import io.bugsbunny.dataScience.service.PackagingService;
import io.bugsbunny.endpoint.AITrafficAgent;
import io.bugsbunny.endpoint.SecurityToken;
import io.bugsbunny.endpoint.SecurityTokenContainer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TrainModelTests {
    private static Logger logger = LoggerFactory.getLogger(TrainModelTests.class);

    @Inject
    private PayloadReplayService payloadReplayService;

    @Inject
    private AITrafficAgent aiTrafficAgent;

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

    @Test
    public void testEvalJava() throws Exception
    {
        String modelPackage = IOUtils.resourceToString("dataScience/aiplatform-model.json", StandardCharsets.UTF_8,
                Thread.currentThread().getContextClassLoader());

        JsonObject input = this.packagingService.performPackaging(modelPackage);
        JsonObject liveModelDeployedJson = this.packagingService.performPackaging(modelPackage);
        long modelId = liveModelDeployedJson.get("modelId").getAsLong();

        String data = IOUtils.resourceToString("dataScience/saturn_data_train.csv", StandardCharsets.UTF_8,
                Thread.currentThread().getContextClassLoader());
        input = new JsonObject();
        input.addProperty("modelId", modelId);
        input.addProperty("format", "csv");
        input.addProperty("data", data);

        Response response = given().body(input.toString()).when().post("/dataset/storeEvalDataSet/").andReturn();
        logger.info("************************");
        logger.info(response.statusLine());
        response.body().prettyPrint();
        logger.info("************************");
        assertEquals(200, response.getStatusCode());
        JsonObject returnValue = JsonParser.parseString(response.body().asString()).getAsJsonObject();
        long dataSetId = returnValue.get("dataSetId").getAsLong();
        input = new JsonObject();
        input.addProperty("modelId", modelId);
        input.addProperty("dataSetId", dataSetId);

        response = given().body(input.toString()).when().post("/trainModel/evalJava").andReturn();
        logger.info("************************");
        logger.info(response.statusLine());
        logger.info("************************");
        assertEquals(200, response.getStatusCode());
    }

    @Test
    public void testEvalPython() throws Exception
    {
        String modelPackage = IOUtils.resourceToString("dataScience/aiplatform-python-model.json", StandardCharsets.UTF_8,
                Thread.currentThread().getContextClassLoader());

        JsonObject modelDeployedJson = this.packagingService.performPackaging(modelPackage);
        long modelId = modelDeployedJson.get("modelId").getAsLong();

        String data = IOUtils.resourceToString("dataScience/saturn_data_train.csv", StandardCharsets.UTF_8,
                Thread.currentThread().getContextClassLoader());
        JsonObject input = new JsonObject();
        input.addProperty("modelId", modelId);
        input.addProperty("format", "csv");
        input.addProperty("data", data);

        Response response = given().body(input.toString()).when().post("/dataset/storeTrainingDataSet/").andReturn();
        logger.info("************************");
        logger.info(response.statusLine());
        response.body().prettyPrint();
        logger.info("modelId: "+modelId);
        logger.info("************************");
        assertEquals(200, response.getStatusCode());
    }
}