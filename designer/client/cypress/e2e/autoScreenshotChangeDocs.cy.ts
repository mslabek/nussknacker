describe("Auto Screenshot Change Docs - ", () => {
    const seed = "autoScreenshotChangeDocs";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.viewport(1920, 1080);
    });

    afterEach(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("basic components - variable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsVariable#0"); // load scenario
        cy.layoutScenario(); // layout alignment
        takeGraphScreenshot(); // take screenshot of whole graph

        cy.get('[model-id="My first variable declaration"]').dblclick(); // click on node
        cy.get('[title="Name"]').click(); // click of remove cursor flickering effect
        takeWindowScreenshot(); // take screenshot of node window

        cy.visitNewProcess(seed, "docsBasicComponentsVariable#1"); // load new scenario
        cy.get('[model-id="only financial ops"]').dblclick(); // click on node
        cy.get('[title="Name"]').click(); // click of remove cursor flickering effect
        takeWindowScreenshot(); // take screenshot of node window
    });

    it("basic components - mapmariable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsMapVariable#0");
        cy.layoutScenario();
        cy.get('[model-id="node label goes here"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();

        cy.get('[model-id="variable"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - filter", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsFilter#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.visitNewProcess(seed, "docsBasicComponentsFilter#1");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="conditional filter"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - choice", () => {
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsChoice#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="choice"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - split", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsSplit#0");
        cy.layoutScenario();
        takeGraphScreenshot();
    });

    it("basic components - foreach", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsForEach#0");
        cy.layoutScenario();
        cy.get('[model-id="for-each"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - union", () => {
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsUnion#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="union"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("aggregates - Single Side Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesSingleSideJoin#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="single-side-join"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("aggregates - Full Outer Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesFullOuterJoin#0");
        cy.layoutScenario();
        cy.get('[model-id="full-outer-join"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("fragments - Inputs", () => {
        cy.visitNewProcess(seed, "docsFragmentsInputs#0");
        cy.layoutScenario();
        cy.get('[model-id="input"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("fragments - Outputs", () => {
        cy.visitNewProcess(seed, "docsFragmentsOutputs#0");
        cy.layoutScenario();
        cy.get('[model-id="output"]').dblclick();
        cy.get('[title="Name"]').click();
        takeGraphScreenshot();
    });
});

// screenshots CONSTANT options DO NOT CHANGE

const snapshotOptions = {
    maxDiffThreshold: 0.00001,
    imagesPath: "../../docs/autoScreenshotChangeDocs",
};

// screenshots taking functions

function takeGraphScreenshot() {
    cy.get('[joint-selector="layers"]').matchImage({
        ...snapshotOptions,
        screenshotConfig: { padding: 16 },
    });
}

function takeWindowScreenshot() {
    cy.get('[data-testid="window-frame"]').matchImage(snapshotOptions);
}
