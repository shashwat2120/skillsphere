package com.skillsphere.content.domain;

/** How a lesson delivers its content. */
public enum LessonType {
    TEXT,
    /** An embedded URL. Video is never hosted here — storage, bandwidth and
     *  transcoding are the one part of this product that costs real money, and
     *  it is not what differentiates it. */
    VIDEO,
    RESOURCE,
    INTERACTIVE
}
