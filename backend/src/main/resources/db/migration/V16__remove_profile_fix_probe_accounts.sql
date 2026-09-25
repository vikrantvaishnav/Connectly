-- Sweep of the bug-reproduction and null-patch verification accounts created
-- while confirming the profile-save fixes on production. As with V14/V15,
-- verified afterwards purely via failed logins — no further accounts created.

DELETE FROM users WHERE username LIKE 'bugrep%'
   OR username LIKE 'fixcheck%'
   OR username LIKE 'nullcheck%';
