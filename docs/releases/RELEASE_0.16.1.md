# 0.16.1-alpha: widget content zoom and dragging

Refresh existing widget hosts and background geometry when scale changes, rather than only resizing grid cells. Native size specifications provide the logical content viewport; the host transform applies visual zoom. Provider size notifications keep that logical viewport.

Add free-position group dragging and movement along an attached clock edge on the 8.5 editor. The preview root’s actual transform converts touch coordinates; native default placement retains Samsung’s touch path. Slider controls remain available.

The signed build and 117 JVM checks pass. Native interaction, visual sizing, Apply/reopen and lockscreen checks are pending; no new claim of AOD-phone compatibility.
