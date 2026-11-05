# 0.16.3-alpha: live widget scale preview

Preview widget scale continuously with a temporary view transform while dragging the slider. The native grid, host geometry and provider options update once on release. This avoids rebuilding providers for every progress event.

The shared editor path covers 8.5 and reference 9.0. Temporary transforms are cleared when the panel rebuilds/detaches, before saving and when the editor session resets. Native transitions that take ownership of scale are not overwritten during cleanup.

The user confirmed correct widget sizing and improved dragging in 0.16.2 on the P613. Live scale preview and actual 9.0 phone behavior need device validation.
