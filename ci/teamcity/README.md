# TeamCity pipeline

Provision the TeamCity pipeline with:

```bash
TEAMCITY_TOKEN='<token>' ./ci/teamcity/setup_pipeline.sh
```

The pipeline builds the jar, builds and pushes the Docker image, then updates:

`charts/config/services/ocpp-service/us/version/dev-version.yaml`
