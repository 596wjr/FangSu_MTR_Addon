if (state.doorLightDymMod === undefined) {
    state.doorLightModel = ModelManager.loadPartedRawModel(Resources.manager(), Resources.id(doorLightModel), null);
    state.doorLightDymMod = new DynamicModelHolder();
    state.doorLightDymMod.uploadLater(state.doorLightModel.get(doorLightSubModel));
}
if ((state.doorVal >= 0.2 && state.doorVal <= 0.4) || (state.doorVal >= 0.6 && state.doorVal <= 0.8) || state.doorVal >= 1) {
    if (state.doorLightDymMod.getUploadedModel() != null) {
        ctx.drawModel(state.doorLightDymMod, null);
        ctx.setDebugInfo("doorlight", "on" + String(state.doorVal));
    }
} else {
    ctx.setDebugInfo("doorlight", "off" + String(state.doorVal));
}
