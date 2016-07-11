var jsMessageRoutes = {}; (function(_root){
    var _nS = function(c,f,b){var e=c.split(f||"."),g=b||_root,d,a;for(d=0,a=e.length;d<a;d++){g=g[e[d]]=g[e[d]]||{}}return g}
    var _qS = function(items){var qs = ''; for(var i=0;i<items.length;i++) {if(items[i]) qs += (qs ? '&' : '') + items[i]}; return qs ? ('?' + qs) : ''}
    var _s = function(p,s){return p+((s===true||(s&&s.secure))?'s':'')+'://'}
    var _wA = function(r){return {ajax:function(c){c=c||{};c.url=r.url;c.type=r.method;return jQuery.ajax(c)}, method:r.method,type:r.method,url:r.url,absoluteURL: function(s){return _s('http',s)+'10.79.2.192:8080'+r.url},webSocketURL: function(s){return _s('ws',s)+'10.79.2.192:8080'+r.url}}}
    _nS('controllers.MessageController'); _root.controllers.MessageController.saveUserMessage =
        function(message) {
            return _wA({method:"POST", url:"/" + "messages/saveUserMessage" + _qS([(function(k,v) {return encodeURIComponent(k)+'='+encodeURIComponent(v)})("message", message)])})
        }

    _nS('controllers.MessageController'); _root.controllers.MessageController.listMostRecent =
        function(limit) {
            return _wA({method:"GET", url:"/" + "message/listMostRecent" + _qS([(function(k,v) {return encodeURIComponent(k)+'='+encodeURIComponent(v)})("limit", limit)])})
        }

})(jsMessageRoutes)