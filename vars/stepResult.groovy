// vars/stepResult.groovy

import groovy.json.JsonSlurper

HttpResponse doGetHttpRequest(String requestUrl) {
    URL url = new URL(requestUrl);
    HttpURLConnection connection = url.openConnection();

    connection.setRequestMethod("GET");

    //get the request
    connection.connect();

    //parse the response
    HttpResponse resp = new HttpResponse(connection);

    if(resp.isFailure()) {
        error("\nGET from URL: $requestUrl\n  HTTP Status: $resp.statusCode\n  Message: $resp.message\n  Response Body: $resp.body");
    }

    return resp;
}

class HttpResponse {

    String body;
    String message;
    Integer statusCode;
    boolean failure = false;

    public HttpResponse(HttpURLConnection connection) {
        this.statusCode = connection.responseCode;
        this.message = connection.responseMessage;

        println message;
        if(statusCode == 200 || statusCode == 201) {
            this.body = connection.content.text;//this would fail the pipeline if there was a 400
        }else{
            this.failure = true;
            this.body = connection.getErrorStream().text;
        }

        connection = null; //set connection to null for good measure, since we are done with it
    }
}

def call(Map config) {

    node {
        def jsonSlurper = new JsonSlurper()
        
        HttpResponse resp = doGetHttpRequest("https://build.hpdd.intel.com/blue/rest/organizations/jenkins/pipelines/daos-stack/pipelines/daos/branches/${env.BRANCH_NAME}/runs/${env.BUILD_ID}/nodes/");
        
        def nodes = jsonSlurper.parseText(resp.getBody())
        assert nodes instanceof List
        
        def node
        for (n in nodes) {
            if (n.state == 'RUNNING') {
                node = n
                break
            }
            println "${n.id} ${n.state} ${n.displayName}"
        }
        println "${node.id} ${node.state} ${node.displayName}"
        
        resp = doGetHttpRequest("https://build.hpdd.intel.com/blue/rest/organizations/jenkins/pipelines/daos-stack/pipelines/daos/branches/${env.BRANCH_NAME}/runs/${env.BUILD_ID}/nodes/${node.id}/steps/");
        
        def steps = jsonSlurper.parseText(resp.getBody())
        assert steps instanceof List
        
        def step
        
        for (s in steps) {
            if (s.state == 'RUNNING') {
                step = s
                break
            }
            println "${s.id} ${s.state} ${s.displayName}"
        }
        println "${step.id} ${step.state} ${step.displayName}"
        for (a in step.actions) {
            if (a.urlName == 'log') {
                println "https://build.hpdd.intel.com${a._links.self.href}?start=0"
            }
        }

        if (!config['ignore_failure']) {
            currentBuild.result = config.get('result')
        }

        if (env.CHANGE_ID) {
           if (config['result'] == "ABORTED" ||
               config['result'] == "UNSTABLE" ||
               config['result'] == "FAILURE") {
                pullRequest.comment("Test stage ${config.name}" +
                                    " completed with status " +
                                    "${config.result}" +
                                    ".  " + env.BUILD_URL +
                                    "display/redirect")
            }

            def result = config['result']
            switch(config['result']) {
                case "UNSTABLE":
                    result = "FAILURE"
                    break
                case "FAILURE":
                    result = "ERROR"
                    break
            }
            githubNotify credentialsId: 'daos-jenkins-commit-status',
                         description: config['name'],
                         context: config['context'] + "/" + config['name'],
                         status: result,
                         targetUrl: 'http://foo.example.com'
        }
    }
}
