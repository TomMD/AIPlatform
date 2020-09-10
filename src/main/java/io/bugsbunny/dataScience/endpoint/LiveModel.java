package io.bugsbunny.dataScience.endpoint;

import io.bugsbunny.dataScience.service.AIModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("liveModel")
public class LiveModel
{
    private static Logger logger = LoggerFactory.getLogger(LiveModel.class);

    @Inject
    private AIModelService aiModelService;

    @Path("eval")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response eval(@RequestBody String input)
    {
        String eval = this.aiModelService.eval();
        Response response = Response.ok(eval).build();
        return response;
    }
}