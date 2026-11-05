# 0.16.2-alpha: nested widget hosts and live dragging

Find widget hosts inside Samsung’s content containers, rather than checking only the outer callback view. Track saved IDs separately from temporary preview host IDs. These changes let existing widgets receive content zoom updates without creating or rebinding IDs.

Update the provider-specific host geometry: Clock Style’s size spec and reference 9.0’s native measured resize record. Native default-scale hosts remain unchanged unless restoring a custom zoom.

Use the native immediate-position flag only while dragging the widget group, and flush pending bindings. Both the 8.5 and reference 9.0 editor mappings were inspected in JADX. The six-case Android nested-host/identity probe passes. Actual editor visuals, persistence and 9.0 phone behavior still need device checks.
