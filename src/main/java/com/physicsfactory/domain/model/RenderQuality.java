package com.physicsfactory.domain.model;

/**
 * How much rendering cost a render is allowed to pay.
 *
 * <p>The distinction is only ever about the cost of drawing frames - how many pixels, how many
 * samples, and whether the expensive raytraced refraction pass runs. It is deliberately <em>not</em>
 * about what the scene contains or how it moves.
 *
 * <p>That boundary matters more here than it looks. The rigid body solver's timestep is one scene
 * frame, so the frame rate and the frame count are simulation inputs, not render settings: lowering
 * either to save time would silently produce a different reel, and a preview that lied about the
 * final result would be worse than no preview. Neither quality touches them.
 */
public enum RenderQuality {

    /** What gets published: full resolution, the template's declared samples, refraction on. */
    PRODUCTION,

    /** What a template author iterates against: fewer pixels, fewer samples, no raytracing. */
    FAST;

    /** The name Blender's engine expects on the command line. */
    public String argument() {
        return name();
    }
}
