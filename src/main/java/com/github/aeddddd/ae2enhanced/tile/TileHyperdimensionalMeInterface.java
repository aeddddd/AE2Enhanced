package com.github.aeddddd.ae2enhanced.tile;

/**
 * 仓储中枢 ME 接口,仅作为 AE 网络物理接入点.
 * 所有网络逻辑(包括存储暴露)委托给控制器.
 */
public class TileHyperdimensionalMeInterface extends TileDelegatedProxyBase<TileHyperdimensionalController> {

    @Override
    protected Class<TileHyperdimensionalController> getControllerClass() {
        return TileHyperdimensionalController.class;
    }

    @Override
    protected boolean isControllerFormed(TileHyperdimensionalController controller) {
        return controller.isFormed();
    }
}
