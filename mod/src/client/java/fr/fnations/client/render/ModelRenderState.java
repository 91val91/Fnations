package fr.fnations.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** État de rendu commun : un modèle d'item orienté, avec un éventuel rotor. */
public class ModelRenderState extends EntityRenderState {
    public final ItemStackRenderState model = new ItemStackRenderState();
    public final ItemStackRenderState rotor = new ItemStackRenderState();
    public boolean hasRotor;
    public float yaw, pitch, roll, rotorAngle, scale = 1, lift;
}
